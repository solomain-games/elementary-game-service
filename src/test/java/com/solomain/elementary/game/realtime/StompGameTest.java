package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.game.GameRepository;
import com.solomain.elementary.game.game.InMemoryGameRepository;
import com.solomain.elementary.game.room.CreateRoomRequest;
import com.solomain.elementary.game.room.InMemoryRoomRepository;
import com.solomain.elementary.game.room.RoomRepository;
import com.solomain.elementary.game.room.RoomService;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.security.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Г7 целиком: двое игроков проходят партию дела-заглушки по STOMP от «Начать» до итогов —
 * так же, как это будет делать фронтенд: смотрят в свой снимок и отправляют команды.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompGameTest {

    private static final PlayerIdentity ANNA = new PlayerIdentity("g-anna", "Анна");
    private static final PlayerIdentity BORIS = new PlayerIdentity("g-boris", "Борис");

    @TestConfiguration
    static class InMemoryStorage {

        @Bean
        @Primary
        RoomRepository inMemoryRoomRepository() {
            return new InMemoryRoomRepository();
        }

        @Bean
        @Primary
        GameRepository inMemoryGameRepository() {
            return new InMemoryGameRepository();
        }
    }

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("elementary.jwt.jwk-set-uri", () -> TestTokens.JWKS_URI);
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RoomService rooms;

    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
    }

    @Test
    @DisplayName("партия от старта до финала: ходы, ошибки только отправителю, голосование, ответы, итоги")
    void fullGame() throws Exception {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        rooms.join(BORIS, code);
        TestPlayer anna = TestPlayer.connect(port, ANNA, clients);
        TestPlayer boris = TestPlayer.connect(port, BORIS, clients);
        anna.enterRoom(code);
        boris.enterRoom(code);
        anna.awaitSnapshot(s -> s.get("room").get("members").size() == 2);

        // Раздача: предыстория, каждый видит только свою руку
        rooms.start(ANNA, code);
        JsonNode prologue = anna.awaitGame(g -> g.get("phase").asString().equals("PROLOGUE"));
        assertThat(prologue.get("viewerId").asString()).isEqualTo("g-anna");
        assertThat(prologue.get("hand").size()).isEqualTo(2);
        assertThat(prologue.get("table").get(0).get("caption").get("ru").asString())
                .isEqualTo("Пустая шкатулка в спальне");   // тексты — объект переводов, как в протоколе

        // «Начать» может только хозяин
        boris.send(code, "begin", Map.of("requestId", "b1"));
        assertThat(boris.awaitError().get("code").asString()).isEqualTo("NOT_HOST");
        anna.send(code, "begin", Map.of());
        JsonNode game = anna.awaitGame(g -> g.get("phase").asString().equals("PLAYING"));

        // Ход не в свою очередь: ошибка приходит только тому, кто ошибся
        TestPlayer waiting = game.get("currentPlayerId").asString().equals("g-anna") ? boris : anna;
        JsonNode waitingGame = waiting == anna ? game
                : waiting.awaitGame(g -> g.get("phase").asString().equals("PLAYING"));
        waiting.send(code, "play", Map.of("requestId", "w1",
                "cardNumber", waitingGame.get("hand").get(0).get("number").asInt()));
        JsonNode notYourTurn = waiting.awaitError();
        assertThat(notYourTurn.get("code").asString()).isEqualTo("NOT_YOUR_TURN");
        assertThat(notYourTurn.get("requestId").asString()).isEqualTo("w1");

        // Ходы по очереди, пока карты не кончатся: нечётные ходы — выложить, чётные — сбросить
        int moves = 0;
        while (game.get("phase").asString().equals("PLAYING")) {
            assertThat(moves).as("слишком много ходов").isLessThan(10);
            int turn = game.get("turnNumber").asInt();
            TestPlayer mover = game.get("currentPlayerId").asString().equals("g-anna") ? anna : boris;
            // Снимок Анны за этот ход уже получен (game), повторно его из очереди не достать.
            JsonNode own = mover == anna ? game : mover.awaitGame(g -> g.get("turnNumber").asInt() == turn);
            int card = own.get("hand").get(0).get("number").asInt();
            mover.send(code, moves % 2 == 0 ? "play" : "discard", Map.of("cardNumber", card));
            moves++;
            game = anna.awaitGame(g -> g.get("turnNumber").asInt() > turn
                    || !g.get("phase").asString().equals("PLAYING"));
        }
        assertThat(game.get("phase").asString()).isEqualTo("NO_MOVES_LEFT");
        assertThat(game.get("table").size()).isEqualTo(3);     // карта №1 и две выложенные
        assertThat(game.get("discardSize").asInt()).isEqualTo(2);
        anna.awaitEvent("CARD_PLAYED");
        anna.awaitEvent("NO_MOVES_LEFT");

        // Голосование за переход к ответам
        anna.send(code, "startVote", Map.of());
        anna.awaitEvent("VOTE_STARTED");
        anna.send(code, "castVote", Map.of("inFavor", true));    // если голос инициатора уже учтён — ALREADY_VOTED, не страшно
        boris.send(code, "castVote", Map.of("inFavor", true));
        anna.awaitGame(g -> g.get("phase").asString().equals("ANSWERING"));
        anna.awaitEvent("VOTE_PASSED");

        // Ответы и итоги
        Map<String, Object> answers = Map.of("answers", Map.of("culprit", "guest", "motive", "debt"));
        anna.send(code, "submitAnswers", answers);
        boris.send(code, "submitAnswers", Map.of("answers", Map.of("culprit", "owner", "motive", "debt")));

        JsonNode finished = boris.awaitGame(g -> g.get("phase").asString().equals("FINISHED"));
        JsonNode reveal = finished.get("reveal");
        assertThat(reveal.get("scores").get("g-anna").asInt()).isEqualTo(2);
        assertThat(reveal.get("scores").get("g-boris").asInt()).isEqualTo(1);
        assertThat(reveal.get("correctAnswers").get("culprit").asString()).isEqualTo("guest");
        assertThat(reveal.get("allCards").size()).isEqualTo(5);
        assertThat(reveal.get("solution").get("ru").asString()).contains("[[1]]");
        anna.awaitEvent("RESULTS_SHOWN");
        anna.awaitSnapshot(s -> s.get("room").get("status").asString().equals("FINISHED"));
    }
}

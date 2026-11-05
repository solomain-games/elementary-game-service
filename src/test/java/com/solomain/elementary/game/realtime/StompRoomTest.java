package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.CreateRoomRequest;
import com.solomain.elementary.game.room.InMemoryRoomRepository;
import com.solomain.elementary.game.room.RoomRepository;
import com.solomain.elementary.game.room.RoomService;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.security.TestTokens;
import com.solomain.elementary.game.timer.InMemoryTimerQueue;
import com.solomain.elementary.game.timer.TimerQueue;
import org.junit.jupiter.api.AfterEach;
import com.solomain.elementary.game.game.GameRepository;
import com.solomain.elementary.game.game.InMemoryGameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Г5 целиком: настоящие STOMP-клиенты подключаются к комнате, получают снимки и события.
 * Комнаты хранятся в памяти вместо Redis; действия в зале ожидания вызываются через RoomService —
 * так же, как это делает REST-контроллер.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompRoomTest {

    private static final long TIMEOUT_SECONDS = 5;

    private static final PlayerIdentity ANNA = new PlayerIdentity("g-anna", "Анна");
    private static final PlayerIdentity BORIS = new PlayerIdentity("g-boris", "Борис");

    @TestConfiguration
    static class InMemoryRooms {

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

        @Bean
        @Primary
        TimerQueue inMemoryTimerQueue() {
            return new InMemoryTimerQueue();
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
    @DisplayName("два клиента: вход второго — событие и снимок у первого; «в сети» следует за подключением")
    void twoClients() throws Exception {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        TestPlayer anna = connect(ANNA);
        anna.enterRoom(code);

        JsonNode first = anna.awaitSnapshot(s -> s.get("room").get("members").size() == 1);
        assertThat(first.get("protocolVersion").asInt()).isEqualTo(1);
        assertThat(first.get("room").get("hostId").asString()).isEqualTo("g-anna");
        assertThat(first.get("room").get("status").asString()).isEqualTo("WAITING");
        assertThat(first.get("game").isNull()).isTrue();   // поле есть, значение null (протокол, 2)

        rooms.join(BORIS, code);

        JsonNode joined = anna.awaitEvent("PLAYER_JOINED");
        assertThat(joined.get("playerId").asString()).isEqualTo("g-boris");
        assertThat(joined.get("name").asString()).isEqualTo("Борис");
        anna.awaitSnapshot(s -> offline(s, "g-boris"));   // вошёл по REST, но ещё не подключился

        TestPlayer boris = connect(BORIS);
        boris.enterRoom(code);
        // Борис подключился — у Анны он «в сети», и Борис видит себя в комнате
        anna.awaitSnapshot(s -> online(s, "g-boris"));
        boris.awaitSnapshot(s -> s.get("room").get("members").size() == 2);

        boris.session.disconnect();
        anna.awaitSnapshot(s -> offline(s, "g-boris"));
    }

    @Test
    @DisplayName("версии снимков растут вместе с изменениями комнаты")
    void versions() throws Exception {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        TestPlayer anna = connect(ANNA);
        anna.enterRoom(code);
        anna.awaitSnapshot(s -> s.get("version").asLong() == 0);

        rooms.join(BORIS, code);
        rooms.start(ANNA, code);

        anna.awaitEvent("GAME_STARTED");
        JsonNode started = anna.awaitSnapshot(s -> s.get("room").get("status").asString().equals("IN_GAME"));
        assertThat(started.get("version").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("исключённый получает PLAYER_KICKED")
    void kicked() throws Exception {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        rooms.join(BORIS, code);
        TestPlayer boris = connect(BORIS);
        boris.enterRoom(code);
        boris.awaitSnapshot(s -> true);

        rooms.kick(ANNA, code, "g-boris");

        assertThat(boris.awaitEvent("PLAYER_KICKED").get("playerId").asString()).isEqualTo("g-boris");
    }

    @Test
    @DisplayName("подписка на чужую комнату — ERROR NOT_IN_ROOM")
    void foreignRoom() throws Exception {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        TestPlayer boris = connect(BORIS);

        boris.session.subscribe(RoomTopics.roomTopic(code), boris.handler(new LinkedBlockingQueue<>()));

        assertThat(boris.stompError.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("NOT_IN_ROOM");
    }

    @Test
    @DisplayName("ошибки команд — только отправителю, с requestId")
    void commandErrors() throws Exception {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        TestPlayer anna = connect(ANNA);
        anna.enterRoom(code);
        anna.awaitSnapshot(s -> true);
        TestPlayer boris = connect(BORIS);
        boris.subscribePersonal();

        anna.session.send("/app/room/" + code + "/dance", Map.of("requestId", "r1"));
        anna.session.send("/app/room/" + code + "/play", Map.of("requestId", "r2", "cardNumber", 3));
        boris.session.send("/app/room/" + code + "/sync", Map.of("requestId", "r3"));

        // Кадры одной сессии сервер обрабатывает параллельно, поэтому ответы могут прийти
        // в любом порядке — по requestId клиент и понимает, на какую команду ответ
        Map<String, JsonNode> byRequest = new java.util.HashMap<>();
        for (int i = 0; i < 2; i++) {
            JsonNode error = anna.awaitError();
            byRequest.put(error.get("requestId").asString(), error);
        }
        // JsonNode сам по себе Iterable, поэтому assertThat(node) выбрал бы проверки коллекций:
        // проверяем поля по одному
        assertThat(byRequest).containsOnlyKeys("r1", "r2");
        assertThat(byRequest.get("r1").get("command").asString()).isEqualTo("dance");
        assertThat(byRequest.get("r1").get("code").asString()).isEqualTo("UNKNOWN_COMMAND");
        assertThat(byRequest.get("r2").get("command").asString()).isEqualTo("play");
        assertThat(byRequest.get("r2").get("code").asString()).isEqualTo("GAME_NOT_STARTED");
        assertThat(boris.awaitError().get("code").asString()).isEqualTo("NOT_IN_ROOM");
    }

    // ---------- тестовый клиент ----------

    private static JsonNode member(JsonNode snapshot, String playerId) {
        JsonNode members = snapshot.get("room").get("members");
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).get("id").asString().equals(playerId)) {
                return members.get(i);
            }
        }
        return null;
    }

    /** Игрок есть в снимке и в сети. */
    private static boolean online(JsonNode snapshot, String playerId) {
        JsonNode member = member(snapshot, playerId);
        return member != null && member.get("online").asBoolean();
    }

    /** Игрок есть в снимке и не в сети. */
    private static boolean offline(JsonNode snapshot, String playerId) {
        JsonNode member = member(snapshot, playerId);
        return member != null && !member.get("online").asBoolean();
    }

    private TestPlayer connect(PlayerIdentity player) throws Exception {
        return TestPlayer.connect(port, player, clients);
    }
}

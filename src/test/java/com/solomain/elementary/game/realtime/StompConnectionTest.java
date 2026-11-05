package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.game.GameRepository;
import com.solomain.elementary.game.game.InMemoryGameRepository;
import com.solomain.elementary.game.room.CreateRoomRequest;
import com.solomain.elementary.game.room.InMemoryRoomRepository;
import com.solomain.elementary.game.room.RoomRepository;
import com.solomain.elementary.game.room.RoomService;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.security.TestTokens;
import com.solomain.elementary.game.timer.GameTimer;
import com.solomain.elementary.game.timer.InMemoryTimerQueue;
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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Г10: закрыл вкладку — остальные видят «отключён»; открыл снова — вернулся со своими картами.
 * Срок ожидания укорочен до 2 секунд, чтобы проверить и «не вернулся»; планировщик таймеров
 * включён (в профиле test он выключен) и работает с очередью в памяти.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"elementary.timers.enabled=true", "elementary.timers.disconnect-timeout=2s"})
@ActiveProfiles("test")
class StompConnectionTest {

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

        @Bean
        @Primary
        InMemoryTimerQueue inMemoryTimerQueue() {   // точный тип — чтобы тест мог получить её через @Autowired
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

    @Autowired
    private InMemoryTimerQueue timers;

    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
    }

    @Test
    @DisplayName("закрыл вкладку — остальные видят «отключён»; открыл снова — вернулся со своими картами")
    void reconnect() throws Exception {
        String code = newRoom();
        TestPlayer anna = enter(ANNA, code);
        TestPlayer boris = enter(BORIS, code);
        start(anna, code);
        String hand = boris.awaitGame(g -> true).get("hand").toString();

        boris.session.disconnect();
        anna.awaitEvent("PLAYER_DISCONNECTED");
        JsonNode seen = anna.awaitGame(g -> status(g, BORIS).equals("DISCONNECTED"));
        assertThat(player(seen, BORIS).get("handSize").asInt()).isEqualTo(2);   // карты остались у него
        // Таймер ставится сразу после рассылки снимка, поэтому снимок может прийти раньше — ждём
        awaitTrue(() -> timers.contains(new GameTimer.DisconnectWait(code, BORIS.id())), "disconnect timer");

        TestPlayer borisAgain = enter(BORIS, code);
        anna.awaitEvent("PLAYER_RECONNECTED");
        JsonNode back = borisAgain.awaitGame(g -> status(g, BORIS).equals("ACTIVE"));
        assertThat(back.get("hand").toString()).isEqualTo(hand);
        assertThat(timers.contains(new GameTimer.DisconnectWait(code, BORIS.id()))).isFalse();
    }

    @Test
    @DisplayName("не вернулся за отведённое время — для партии вышел")
    void timeout() throws Exception {
        String code = newRoom();
        TestPlayer anna = enter(ANNA, code);
        TestPlayer boris = enter(BORIS, code);
        start(anna, code);

        boris.session.disconnect();
        anna.awaitEvent("PLAYER_DISCONNECTED");
        anna.awaitEvent("PLAYER_GAVE_UP");
        JsonNode game = anna.awaitGame(g -> status(g, BORIS).equals("LEFT"));
        assertThat(player(game, BORIS).get("handSize").asInt()).isZero();   // карты ушли в резерв
    }

    @Test
    @DisplayName("две вкладки: закрытие одной ничего не меняет, закрытие обеих — отключение")
    void twoTabs() throws Exception {
        String code = newRoom();
        TestPlayer anna = enter(ANNA, code);
        TestPlayer tab1 = enter(BORIS, code);
        TestPlayer tab2 = enter(BORIS, code);
        start(anna, code);

        tab1.session.disconnect();
        // Отрицательную проверку («ничего не пришло») по-другому не сделать: даём серверу время
        // обработать отключение и смотрим, что события не было
        Thread.sleep(500);
        assertThat(anna.events).noneMatch(e -> e.get("type").asString().equals("PLAYER_DISCONNECTED"));
        assertThat(timers.contains(new GameTimer.DisconnectWait(code, BORIS.id()))).isFalse();

        tab2.session.disconnect();
        anna.awaitEvent("PLAYER_DISCONNECTED");
    }

    // ---------- общее ----------

    private static void awaitTrue(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TestPlayer.TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No " + what + " within " + TestPlayer.TIMEOUT_SECONDS + " s");
            }
            Thread.sleep(20);
        }
    }

    private String newRoom() {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, null)).code();
        rooms.join(BORIS, code);
        return code;
    }

    /** Подключение как у фронтенда; ждёт, пока сервер увидит игрока в сети. */
    private TestPlayer enter(PlayerIdentity player, String code) throws Exception {
        TestPlayer client = TestPlayer.connect(port, player, clients);
        client.enterRoom(code);
        client.awaitSnapshot(s -> isOnline(s, player));
        return client;
    }

    private void start(TestPlayer anna, String code) throws InterruptedException {
        anna.awaitSnapshot(s -> isOnline(s, BORIS));   // Борис подключён до старта
        rooms.start(ANNA, code);
        anna.awaitGame(g -> g.get("phase").asString().equals("PROLOGUE"));
    }

    private static boolean isOnline(JsonNode snapshot, PlayerIdentity player) {
        for (JsonNode member : snapshot.get("room").get("members")) {
            if (member.get("id").asString().equals(player.id())) {
                return member.get("online").asBoolean();
            }
        }
        return false;
    }

    private static String status(JsonNode game, PlayerIdentity player) {
        return player(game, player).get("status").asString();
    }

    private static JsonNode player(JsonNode game, PlayerIdentity player) {
        for (JsonNode p : game.get("players")) {
            if (p.get("id").asString().equals(player.id())) {
                return p;
            }
        }
        throw new AssertionError("No player " + player.id() + " in " + game);
    }
}

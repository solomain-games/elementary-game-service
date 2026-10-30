package com.solomain.elementary.game.realtime;

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

        // JsonNode сам по себе Iterable, поэтому assertThat(node) выбрал бы проверки коллекций:
        // проверяем поля по одному
        JsonNode unknown = anna.awaitError();
        assertThat(unknown.get("requestId").asString()).isEqualTo("r1");
        assertThat(unknown.get("command").asString()).isEqualTo("dance");
        assertThat(unknown.get("code").asString()).isEqualTo("UNKNOWN_COMMAND");
        assertThat(anna.awaitError().get("code").asString()).isEqualTo("GAME_NOT_STARTED");
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
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        clients.add(client);

        var testPlayer = new TestPlayer();
        var headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + TestTokens.valid(player.id(), player.name()));
        testPlayer.session = client.connectAsync(URI.create("ws://localhost:" + port + "/ws"), null, headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders frameHeaders, Object payload) {
                        testPlayer.stompError.complete(frameHeaders.getFirst("message"));   // только ERROR
                    }
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return testPlayer;
    }

    /** Клиент одного игрока: подписки как у фронтенда и очереди полученных сообщений. */
    private static final class TestPlayer {

        StompSession session;
        final CompletableFuture<String> stompError = new CompletableFuture<>();
        final BlockingQueue<JsonNode> snapshots = new LinkedBlockingQueue<>();
        final BlockingQueue<JsonNode> errors = new LinkedBlockingQueue<>();
        final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();

        void subscribePersonal() {
            session.subscribe("/user/queue/state", handler(snapshots));
            session.subscribe("/user/queue/errors", handler(errors));
        }

        /** Как фронтенд после подключения: подписки и sync. */
        void enterRoom(String code) {
            subscribePersonal();
            session.subscribe(RoomTopics.roomTopic(code), handler(events));
            session.send("/app/room/" + code + "/sync", Map.of());
        }

        StompFrameHandler handler(BlockingQueue<JsonNode> queue) {
            return new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((JsonNode) payload);
                }
            };
        }

        /** Ждёт снимок, подходящий под условие; более ранние пропускает. */
        JsonNode awaitSnapshot(Predicate<JsonNode> condition) throws InterruptedException {
            return await(snapshots, condition, "snapshot");
        }

        JsonNode awaitEvent(String type) throws InterruptedException {
            return await(events, event -> event.get("type").asString().equals(type), "event " + type);
        }

        JsonNode awaitError() throws InterruptedException {
            return await(errors, error -> true, "command error");
        }

        private static JsonNode await(BlockingQueue<JsonNode> queue, Predicate<JsonNode> condition, String what)
                throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                JsonNode message = queue.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && condition.test(message)) {
                    return message;
                }
            }
            throw new AssertionError("No " + what + " within " + TIMEOUT_SECONDS + " s");
        }
    }
}

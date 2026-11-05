package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.security.TestTokens;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Тестовый STOMP-клиент одного игрока: подписки как у фронтенда и очереди полученных сообщений.
 * Общий для тестов WebSocket ({@link StompRoomTest}, {@link StompGameTest}).
 */
final class TestPlayer {

    static final long TIMEOUT_SECONDS = 5;

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

    /** Ждёт снимок, в котором есть партия и она подходит под условие. */
    JsonNode awaitGame(Predicate<JsonNode> condition) throws InterruptedException {
        return awaitSnapshot(s -> !s.get("game").isNull() && condition.test(s.get("game"))).get("game");
    }

    /** Команда игры, как её отправляет фронтенд. */
    void send(String code, String command, Map<String, Object> body) {
        session.send("/app/room/" + code + "/" + command, body);
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

    static TestPlayer connect(int port, PlayerIdentity player, List<WebSocketStompClient> clients)
            throws Exception {
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

                    // Если ERROR не дошёл, а соединение оборвалось или кадр не разобрался, тест упадёт
                    // с настоящей причиной, а не с безликим таймаутом
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders frameHeaders,
                                                byte[] payload, Throwable exception) {
                        testPlayer.stompError.completeExceptionally(
                                new AssertionError("STOMP " + command + " " + frameHeaders + " failed", exception));
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        testPlayer.stompError.completeExceptionally(
                                new AssertionError("Transport error before ERROR frame", exception));
                    }
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return testPlayer;
    }
}

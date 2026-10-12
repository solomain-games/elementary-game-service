package com.solomain.elementary.game.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка токена при подключении по STOMP. Клиент — настоящий STOMP-клиент Spring
 * поверх WebSocket, как браузер с {@code @stomp/stompjs}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StompAuthTest.WhoAmIStompController.class)
class StompAuthTest {

    private static final long TIMEOUT_SECONDS = 5;

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("elementary.jwt.jwk-set-uri", () -> TestTokens.JWKS_URI);
    }

    @Value("${local.server.port}")
    private int port;

    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    @Test
    @DisplayName("действительный токен: подключение принято, игрок определён по токену")
    void validToken() throws Exception {
        var errors = new CompletableFuture<String>();
        StompSession session = connect(TestTokens.valid("g-test1", "Анна"), errors)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        var reply = new CompletableFuture<String>();
        session.subscribe("/app/test/whoami", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                reply.complete((String) payload);
            }
        });

        assertThat(reply.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("g-test1");
        session.disconnect();
    }

    @Test
    @DisplayName("без токена: кадр ERROR с message:UNAUTHORIZED")
    void noToken() throws Exception {
        assertRejected(null);
    }

    @Test
    @DisplayName("не JWT вовсе: ERROR UNAUTHORIZED")
    void garbageToken() throws Exception {
        assertRejected("not-a-jwt");
    }

    @Test
    @DisplayName("подпись чужим ключом: ERROR UNAUTHORIZED")
    void forgedToken() throws Exception {
        assertRejected(TestTokens.signedByForeignKey());
    }

    @Test
    @DisplayName("истёкший токен: ERROR UNAUTHORIZED")
    void expiredToken() throws Exception {
        assertRejected(TestTokens.expired());
    }

    private void assertRejected(String token) throws Exception {
        var errors = new CompletableFuture<String>();
        connect(token, errors);
        assertThat(errors.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("UNAUTHORIZED");
    }

    /**
     * Подключается с токеном в кадре CONNECT. Заголовок {@code message} кадра ERROR
     * попадает в {@code errors}.
     */
    private CompletableFuture<StompSession> connect(String token, CompletableFuture<String> errors) {
        var connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync(URI.create("ws://localhost:" + port + "/ws"), null, connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errors.complete(headers.getFirst("message"));   // сюда приходят только ERROR
                    }
                });
    }

    /**
     * Контроллер только для теста. {@code @SubscribeMapping} отвечает прямо на подписку,
     * поэтому не нужно ждать, пока подписка дойдёт до брокера.
     */
    @Controller
    static class WhoAmIStompController {

        @SubscribeMapping("/test/whoami")
        String whoAmI(Principal principal) {
            return principal.getName();
        }
    }
}

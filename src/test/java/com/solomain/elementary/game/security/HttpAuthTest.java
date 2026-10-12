package com.solomain.elementary.game.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка токенов в REST. Сервис поднимается целиком на случайном порту, открытый ключ
 * берётся из заглушки JWKS ({@link TestTokens}) — тот же путь, что и в работе.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpAuthTest.WhoAmIController.class)
class HttpAuthTest {

    private static final String WHO_AM_I = "/api/game/test/whoami";

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("elementary.jwt.jwk-set-uri", () -> TestTokens.JWKS_URI);
    }

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("действительный токен: игрок и роль берутся из токена")
    void validToken() throws Exception {
        var response = get(WHO_AM_I, TestTokens.valid("g-test1", "Анна"));

        assertThat(response.statusCode()).isEqualTo(200);
        // ROLE_GUEST — из claim role; FACTOR_BEARER Spring Security 7 добавляет сам:
        // «пользователь подтвердил себя bearer-токеном» (основа для многофакторной аутентификации)
        assertThat(response.body()).isEqualTo("g-test1 [FACTOR_BEARER, ROLE_GUEST]");
    }

    @Test
    @DisplayName("без токена: 401 в формате Problem Details с кодом UNAUTHORIZED")
    void noToken() throws Exception {
        var response = get(WHO_AM_I, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                value -> assertThat(value).startsWith("Bearer"));
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("не JWT вовсе: 401")
    void garbageToken() throws Exception {
        assertUnauthorized(get(WHO_AM_I, "not-a-jwt"));
    }

    @Test
    @DisplayName("подпись чужим ключом с тем же kid: 401")
    void forgedToken() throws Exception {
        assertUnauthorized(get(WHO_AM_I, TestTokens.signedByForeignKey()));
    }

    @Test
    @DisplayName("истёкший токен: 401")
    void expiredToken() throws Exception {
        assertUnauthorized(get(WHO_AM_I, TestTokens.expired()));
    }

    @Test
    @DisplayName("токен другого издателя: 401")
    void wrongIssuer() throws Exception {
        assertUnauthorized(get(WHO_AM_I, TestTokens.withIssuer("someone-else")));
    }

    @Test
    @DisplayName("проверка состояния доступна без токена")
    void healthIsPublic() throws Exception {
        // liveness не зависит от Redis, поэтому UP и без него
        assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
    }

    private static void assertUnauthorized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Контроллер только для теста: показывает, кем сервис считает отправителя.
     * Вложенные классы тестов Spring не находит сканированием, поэтому подключён через {@code @Import}.
     */
    @RestController
    static class WhoAmIController {

        @GetMapping(WHO_AM_I)
        String whoAmI(Authentication authentication) {
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .sorted()
                    .toList();
            return authentication.getName() + " " + authorities;
        }
    }
}

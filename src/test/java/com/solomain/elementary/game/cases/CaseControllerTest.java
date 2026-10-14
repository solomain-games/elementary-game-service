package com.solomain.elementary.game.cases;

import com.solomain.elementary.game.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** GET /api/game/cases целиком: сервис на случайном порту, дела из src/test/resources/test-cases. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CaseControllerTest {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("elementary.jwt.jwk-set-uri", () -> TestTokens.JWKS_URI);
    }

    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("список дел в формате протокола (4.2), без ответов и карт")
    void listCases() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/game/cases"))
                .header("Authorization", "Bearer " + TestTokens.valid("g-test1", "Анна"))
                .GET()
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode cases = JsonMapper.builder().build().readTree(response.body());
        assertThat(cases.size()).isEqualTo(1);
        JsonNode demo = cases.get(0);
        assertThat(demo.get("id").asString()).isEqualTo("demo");
        assertThat(demo.get("title").get("ru").asString()).isEqualTo("Пропавший браслет");
        assertThat(demo.get("cover").asString()).isEqualTo("images/cover.webp");
        assertThat(demo.get("recommendedPlayers").get("max").asInt()).isEqualTo(4);
        assertThat(demo.get("durationMinutes").asInt()).isEqualTo(10);
        // Секреты дела наружу не уходят
        assertThat(response.body()).doesNotContain("correct", "relevant", "solution", "cards");
    }
}

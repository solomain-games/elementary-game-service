package com.solomain.elementary.game.room;

import com.solomain.elementary.game.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST комнат целиком: сервис на случайном порту, настоящие токены, комнаты — в памяти
 * вместо Redis (вложенная {@code @TestConfiguration} подменяет хранилище через {@code @Primary}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomControllerTest {

    private static final String ANNA = TestTokens.valid("g-anna", "Анна");
    private static final String BORIS = TestTokens.valid("g-boris", "Борис");
    private static final String VERA = TestTokens.valid("g-vera", "Вера");

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("сценарий из задачи: создал → двое вошли → один исключён → запуск")
    void scenario() throws Exception {
        var created = post("/api/game/rooms", ANNA,
                "{\"caseId\":\"demo\",\"language\":\"ru\",\"maxPlayers\":6,\"turnTimeoutSeconds\":60}");
        assertThat(created.statusCode()).isEqualTo(201);
        String code = JSON.readTree(created.body()).get("code").asString();
        assertThat(created.headers().firstValue("Location")).hasValue("/api/game/rooms/" + code);

        assertThat(post("/api/game/rooms/" + code + "/join", BORIS, null).statusCode()).isEqualTo(204);
        assertThat(post("/api/game/rooms/" + code + "/join", VERA, null).statusCode()).isEqualTo(204);
        assertThat(post("/api/game/rooms/" + code + "/kick", ANNA, "{\"playerId\":\"g-boris\"}").statusCode())
                .isEqualTo(204);
        assertThat(post("/api/game/rooms/" + code + "/start", ANNA, null).statusCode()).isEqualTo(204);

        // Исключённый не может вернуться
        var rejoin = post("/api/game/rooms/" + code + "/join", BORIS, null);
        assertThat(rejoin.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(rejoin.body()).get("code").asString()).isEqualTo("KICKED_FROM_ROOM");

        JsonNode info = JSON.readTree(get("/api/game/rooms/" + code, VERA).body());
        assertThat(info.get("players").asInt()).isEqualTo(2);
        assertThat(info.get("status").asString()).isEqualTo("IN_GAME");
        assertThat(info.get("caseTitle").get("ru").asString()).isEqualTo("Пропавший браслет");
    }

    @Test
    @DisplayName("ошибка правил — Problem Details с машинным кодом")
    void notHost() throws Exception {
        String code = createRoom();

        var response = post("/api/game/rooms/" + code + "/start", BORIS, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("NOT_HOST");
    }

    @Test
    @DisplayName("неверные настройки — VALIDATION_FAILED с полями")
    void validation() throws Exception {
        var response = post("/api/game/rooms", ANNA,
                "{\"caseId\":\"demo\",\"language\":\"ru\",\"maxPlayers\":9,\"turnTimeoutSeconds\":45}");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        JsonNode errors = body.get("errors");
        var fields = new java.util.ArrayList<String>();
        for (int i = 0; i < errors.size(); i++) {
            fields.add(errors.get(i).get("field").asString());
        }
        assertThat(fields).containsExactlyInAnyOrder("maxPlayers", "turnTimeoutValid");
    }

    @Test
    @DisplayName("несуществующая комната — 404 ROOM_NOT_FOUND")
    void roomNotFound() throws Exception {
        var response = get("/api/game/rooms/ZZZZZZ", ANNA);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    @DisplayName("без токена — 401")
    void unauthorized() throws Exception {
        assertThat(get("/api/game/rooms/ZZZZZZ", null).statusCode()).isEqualTo(401);
    }

    private String createRoom() throws Exception {
        var response = post("/api/game/rooms", ANNA, "{\"caseId\":\"demo\",\"language\":\"ru\",\"maxPlayers\":4}");
        return JSON.readTree(response.body()).get("code").asString();
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        return send(request(path, token).GET());
    }

    private HttpResponse<String> post(String path, String token, String body)
            throws IOException, InterruptedException {
        var request = request(path, token);
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return send(request);
    }

    private HttpRequest.Builder request(String path, String token) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

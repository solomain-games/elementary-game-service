package com.solomain.elementary.game.game;

import com.solomain.elementary.core.engine.GameEngine;
import com.solomain.elementary.core.engine.PlayerInfo;
import com.solomain.elementary.core.model.GameSettings;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.cases.CasesProperties;
import com.solomain.elementary.game.support.RedisTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Партии в настоящем Redis (Testcontainers): то, что переживает перезапуск сервиса. */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Партии в Redis")
class RedisGameRepositoryTest {

    private final StringRedisTemplate redis = RedisTestSupport.redis();
    private final RedisGameRepository repository = new RedisGameRepository(redis, RedisTestSupport.ROOMS);

    @Test
    @DisplayName("сохранённая партия читается тем же состоянием — как после перезапуска сервиса")
    void survivesRestart() {
        GameState state = GameEngine.start(demo(),
                List.of(new PlayerInfo("g-anna", "Анна"), new PlayerInfo("g-boris", "Борис")),
                new GameSettings("g-anna", Duration.ofSeconds(60)), new Random(7));
        repository.save("REDIS1", state);

        // Новый экземпляр хранилища ничего не помнит сам — всё берётся из Redis
        var afterRestart = new RedisGameRepository(redis, RedisTestSupport.ROOMS);

        assertThat(afterRestart.find("REDIS1")).contains(state);
    }

    @Test
    @DisplayName("ключ хранится с TTL, удаление работает")
    void ttlAndDelete() {
        GameState state = GameEngine.start(demo(), List.of(new PlayerInfo("g-anna", "Анна")),
                new GameSettings("g-anna", null), new Random(7));
        repository.save("REDIS2", state);

        assertThat(redis.getExpire(RedisGameRepository.key("REDIS2"))).isPositive();

        repository.delete("REDIS2");
        assertThat(repository.find("REDIS2")).isEmpty();
    }

    private static com.solomain.elementary.core.model.CaseDefinition demo() {
        try {
            String dir = Path.of(RedisGameRepositoryTest.class.getResource("/test-cases").toURI()).toString();
            return new CaseCatalog(new CasesProperties(dir)).find("demo").orElseThrow().definition();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}

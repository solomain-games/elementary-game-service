package com.solomain.elementary.game.support;

import com.solomain.elementary.game.room.RoomsProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;

/**
 * Настоящий Redis в Docker для тестов хранилищ (Testcontainers). Контейнер один на все тесты
 * и запускается при первом обращении; Testcontainers сам остановит его после тестов.
 * Тесты с ним помечены {@code @Testcontainers(disabledWithoutDocker = true)}: без Docker
 * они пропускаются, а не падают.
 */
public final class RedisTestSupport {

    public static final RoomsProperties ROOMS = new RoomsProperties(Duration.ofHours(6));

    private static GenericContainer<?> container;
    private static StringRedisTemplate template;

    private RedisTestSupport() {
    }

    public static synchronized StringRedisTemplate redis() {
        if (template == null) {
            container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            container.start();
            var factory = new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            template = new StringRedisTemplate(factory);
        }
        return template;
    }
}

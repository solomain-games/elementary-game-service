package com.solomain.elementary.game.room;

import com.solomain.elementary.game.support.RedisTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Комнаты в настоящем Redis (Testcontainers). */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Комнаты в Redis")
class RedisRoomRepositoryTest {

    private final StringRedisTemplate redis = RedisTestSupport.redis();
    private final RedisRoomRepository repository = new RedisRoomRepository(redis, RedisTestSupport.ROOMS);

    private static Room room(String code) {
        return Room.create(code, "demo", "ru", 4, 60, new Member("g-anna", "Анна", Instant.parse("2026-11-04T12:00:00Z")));
    }

    @Test
    @DisplayName("createIfAbsent: занятый код не перезаписывается")
    void createIfAbsent() {
        assertThat(repository.createIfAbsent(room("ROOM01"))).isTrue();
        assertThat(repository.createIfAbsent(room("ROOM01").withStatus(RoomStatus.IN_GAME))).isFalse();

        assertThat(repository.find("ROOM01").orElseThrow().status()).isEqualTo(RoomStatus.WAITING);
    }

    @Test
    @DisplayName("сохранение, чтение, TTL и удаление")
    void saveFindDelete() {
        Room room = room("ROOM02").withMember(new Member("g-boris", "Борис", Instant.parse("2026-11-04T12:01:00Z")));
        repository.save(room);

        assertThat(repository.find("ROOM02")).contains(room);
        assertThat(redis.getExpire("room:ROOM02")).isPositive();

        repository.delete("ROOM02");
        assertThat(repository.find("ROOM02")).isEmpty();
    }
}

package com.solomain.elementary.game.room;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Комнаты в Redis: ключ {@code room:{код}}, значение — комната в JSON, срок хранения —
 * {@code elementary.rooms.ttl}. Redis сам удаляет ключ по истечении срока (TTL),
 * поэтому заброшенные комнаты не копятся.
 */
@Repository
@EnableConfigurationProperties(RoomsProperties.class)
class RedisRoomRepository implements RoomRepository {

    private static final String KEY_PREFIX = "room:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final JsonMapper json = RoomJson.MAPPER;

    RedisRoomRepository(StringRedisTemplate redis, RoomsProperties properties) {
        this.redis = redis;
        this.ttl = properties.ttl();
    }

    @Override
    public Optional<Room> find(String code) {
        return Optional.ofNullable(redis.opsForValue().get(key(code))).map(value -> json.readValue(value, Room.class));
    }

    @Override
    public boolean createIfAbsent(Room room) {
        // SET key value NX EX ttl — атомарно: из двух одновременных созданий с одним кодом пройдёт одно
        Boolean created = redis.opsForValue().setIfAbsent(key(room.code()), json.writeValueAsString(room), ttl);
        return Boolean.TRUE.equals(created);
    }

    @Override
    public void save(Room room) {
        redis.opsForValue().set(key(room.code()), json.writeValueAsString(room), ttl);
    }

    @Override
    public void delete(String code) {
        redis.delete(key(code));
    }

    private static String key(String code) {
        return KEY_PREFIX + code;
    }
}

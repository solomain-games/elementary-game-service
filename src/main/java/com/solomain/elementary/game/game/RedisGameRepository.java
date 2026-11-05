package com.solomain.elementary.game.game;

import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.game.room.RoomsProperties;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Партии в Redis: ключ {@code game:{код комнаты}}, значение — {@code GameState} в JSON.
 * Срок хранения тот же, что у комнат, и продлевается при каждом ходе. Сервис ничего не держит
 * в памяти между командами — поэтому после перезапуска партия продолжается с того же места.
 */
@Repository
public class RedisGameRepository implements GameRepository {

    private static final String KEY_PREFIX = "game:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final JsonMapper json = GameJson.MAPPER;

    public RedisGameRepository(StringRedisTemplate redis, RoomsProperties properties) {
        this.redis = redis;
        this.ttl = properties.ttl();
    }

    @Override
    public Optional<GameState> find(String roomCode) {
        return Optional.ofNullable(redis.opsForValue().get(key(roomCode)))
                .map(value -> json.readValue(value, GameState.class));
    }

    @Override
    public void save(String roomCode, GameState state) {
        redis.opsForValue().set(key(roomCode), json.writeValueAsString(state), ttl);
    }

    @Override
    public void delete(String roomCode) {
        redis.delete(key(roomCode));
    }

    /**
     * SCAN, а не KEYS: KEYS обходит всю базу за одну команду и на это время останавливает Redis,
     * SCAN отдаёт ключи порциями. Вызывается один раз при старте сервиса.
     */
    @Override
    public Set<String> codes() {
        Set<String> codes = new HashSet<>();
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build())) {
            keys.forEachRemaining(key -> codes.add(key.substring(KEY_PREFIX.length())));
        }
        return codes;
    }

    static String key(String roomCode) {
        return KEY_PREFIX + roomCode;
    }
}

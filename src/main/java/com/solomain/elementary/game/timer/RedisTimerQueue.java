package com.solomain.elementary.game.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Таймеры в Redis: sorted set {@code timers}, элемент — {@link GameTimer#key()}, оценка (score) —
 * срок в миллисекундах. Sorted set упорядочен по оценке, поэтому «всё, что уже наступило» — это
 * просто диапазон от минус бесконечности до текущего момента.
 */
@Repository
public class RedisTimerQueue implements TimerQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisTimerQueue.class);

    static final String KEY = "timers";

    /**
     * Найти наступившие и удалить их — одной атомарной операцией: Redis выполняет скрипт целиком,
     * не прерываясь на другие команды. Так два экземпляра сервиса не заберут один таймер дважды,
     * а таймер, перенесённый в этот момент на новый срок, не потеряется.
     */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> POP_DUE = RedisScript.of("""
            local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            if #due > 0 then
                redis.call('ZREM', KEYS[1], unpack(due))
            end
            return due
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisTimerQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void schedule(GameTimer timer, Instant at) {
        redis.opsForZSet().add(KEY, timer.key(), score(at));
    }

    @Override
    public void cancel(GameTimer timer) {
        redis.opsForZSet().remove(KEY, timer.key());
    }

    @Override
    public List<GameTimer> pollDue(Instant now, int limit) {
        List<?> keys = redis.execute(POP_DUE, List.of(KEY), String.valueOf(now.toEpochMilli()), String.valueOf(limit));
        List<GameTimer> due = new ArrayList<>();
        for (Object key : keys == null ? List.of() : keys) {
            try {
                due.add(GameTimer.parse(key.toString()));
            } catch (IllegalArgumentException e) {
                log.warn("Dropping unreadable timer {}", key, e);
            }
        }
        return due;
    }

    /**
     * Миллисекунды с округлением вверх: ядро не примет таймер раньше срока, даже на долю миллисекунды.
     */
    static long score(Instant at) {
        return at.plusNanos(999_999).toEpochMilli();
    }
}

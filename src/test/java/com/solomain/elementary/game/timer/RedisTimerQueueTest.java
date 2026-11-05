package com.solomain.elementary.game.timer;

import com.solomain.elementary.game.support.RedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Очередь таймеров в настоящем Redis (Testcontainers). */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Таймеры в Redis")
class RedisTimerQueueTest {

    private static final Instant T0 = Instant.parse("2026-11-04T12:00:00Z");

    private static final GameTimer TURN = new GameTimer.TurnEnd("TIMER1", "g-anna", 3);
    private static final GameTimer VOTE = new GameTimer.VoteEnd("TIMER1", T0.plusSeconds(20));
    private static final GameTimer WAIT = new GameTimer.DisconnectWait("TIMER1", "g-boris");

    private final StringRedisTemplate redis = RedisTestSupport.redis();
    private final RedisTimerQueue queue = new RedisTimerQueue(redis);

    @BeforeEach
    void clean() {
        redis.delete(RedisTimerQueue.KEY);
    }

    @Test
    @DisplayName("забирает только наступившие, самые ранние первыми, и удаляет их из очереди")
    void pollDue() {
        queue.schedule(VOTE, T0.plusSeconds(20));
        queue.schedule(TURN, T0.plusSeconds(10));
        queue.schedule(WAIT, T0.plusSeconds(120));

        assertThat(queue.pollDue(T0.plusSeconds(5), 100)).isEmpty();
        assertThat(queue.pollDue(T0.plusSeconds(30), 100)).containsExactly(TURN, VOTE);
        assertThat(queue.pollDue(T0.plusSeconds(30), 100)).isEmpty();   // второй раз не достаются
        assertThat(queue.pollDue(T0.plusSeconds(120), 100)).containsExactly(WAIT);
    }

    @Test
    @DisplayName("тот же таймер ещё раз — перенос срока, а не второй таймер")
    void rescheduleMovesDeadline() {
        queue.schedule(WAIT, T0.plusSeconds(10));
        queue.schedule(WAIT, T0.plusSeconds(60));

        assertThat(queue.pollDue(T0.plusSeconds(30), 100)).isEmpty();
        assertThat(queue.pollDue(T0.plusSeconds(60), 100)).containsExactly(WAIT);
    }

    @Test
    @DisplayName("снятый таймер не срабатывает; снять несуществующий — не ошибка")
    void cancel() {
        queue.schedule(TURN, T0);
        queue.cancel(TURN);
        queue.cancel(WAIT);

        assertThat(queue.pollDue(T0.plusSeconds(60), 100)).isEmpty();
    }

    @Test
    @DisplayName("не больше limit за раз — остальные в следующий")
    void limit() {
        queue.schedule(TURN, T0);
        queue.schedule(VOTE, T0.plusSeconds(1));
        queue.schedule(WAIT, T0.plusSeconds(2));

        assertThat(queue.pollDue(T0.plusSeconds(10), 2)).containsExactly(TURN, VOTE);
        assertThat(queue.pollDue(T0.plusSeconds(10), 2)).containsExactly(WAIT);
    }

    @Test
    @DisplayName("таймеры переживают перезапуск: новый экземпляр видит то, что поставил старый")
    void survivesRestart() {
        queue.schedule(TURN, T0);

        var afterRestart = new RedisTimerQueue(redis);

        assertThat(afterRestart.pollDue(T0, 100)).containsExactly(TURN);
    }

    @Test
    @DisplayName("испорченная запись пропускается, остальные таймеры срабатывают")
    void skipsGarbage() {
        redis.opsForZSet().add(RedisTimerQueue.KEY, "garbage", 0);
        queue.schedule(TURN, T0);

        assertThat(queue.pollDue(T0, 100)).containsExactly(TURN);
        assertThat(redis.opsForZSet().size(RedisTimerQueue.KEY)).isZero();
    }
}

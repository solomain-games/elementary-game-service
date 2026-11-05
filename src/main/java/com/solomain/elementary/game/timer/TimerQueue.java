package com.solomain.elementary.game.timer;

import java.time.Instant;
import java.util.List;

/** Очередь таймеров по сроку. В работе — Redis ({@link RedisTimerQueue}), в тестах — память. */
public interface TimerQueue {

    /** Ставит таймер; если такой уже стоит — переносит его срок. */
    void schedule(GameTimer timer, Instant at);

    /** Снимает таймер; снять несуществующий — не ошибка. */
    void cancel(GameTimer timer);

    /**
     * Забирает таймеры, срок которых наступил, — не больше {@code limit}, самые ранние первыми.
     * Забранные из очереди удаляются: каждый таймер достаётся только одному вызову.
     */
    List<GameTimer> pollDue(Instant now, int limit);
}

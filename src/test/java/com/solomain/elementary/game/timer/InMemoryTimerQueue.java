package com.solomain.elementary.game.timer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Очередь таймеров в памяти — для тестов без Redis. Ведёт себя как {@link RedisTimerQueue}. */
public class InMemoryTimerQueue implements TimerQueue {

    private final Map<GameTimer, Instant> timers = new HashMap<>();

    @Override
    public synchronized void schedule(GameTimer timer, Instant at) {
        timers.put(timer, at);
    }

    @Override
    public synchronized void cancel(GameTimer timer) {
        timers.remove(timer);
    }

    @Override
    public synchronized List<GameTimer> pollDue(Instant now, int limit) {
        List<GameTimer> due = timers.entrySet().stream()
                .filter(entry -> !entry.getValue().isAfter(now))
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
        due.forEach(timers::remove);
        return new ArrayList<>(due);
    }

    /** Что сейчас стоит в очереди и на какой срок. */
    public synchronized Map<GameTimer, Instant> scheduled() {
        return Map.copyOf(timers);
    }

    public synchronized boolean contains(GameTimer timer) {
        return timers.containsKey(timer);
    }
}

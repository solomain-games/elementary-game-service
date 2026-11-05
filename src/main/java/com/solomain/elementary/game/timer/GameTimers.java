package com.solomain.elementary.game.timer;

import com.solomain.elementary.core.model.GameState;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Таймеры, которые следуют из состояния партии: срок хода и срок голосования ядро хранит
 * в самом {@link GameState}. После каждой принятой команды старый набор сравнивается с новым.
 */
public final class GameTimers {

    private GameTimers() {
    }

    /** Таймеры партии и их сроки. */
    public static Map<GameTimer, Instant> of(String roomCode, GameState state) {
        Map<GameTimer, Instant> timers = new HashMap<>();
        if (state.turnDeadline() != null) {
            String current = state.players().get(state.currentPlayerIndex()).id();
            timers.put(new GameTimer.TurnEnd(roomCode, current, state.turnNumber()), state.turnDeadline());
        }
        if (state.vote() != null) {
            timers.put(new GameTimer.VoteEnd(roomCode, state.vote().deadline()), state.vote().deadline());
        }
        return timers;
    }

    /** Снимает таймеры, которых больше нет, и ставит новые (или с новым сроком). */
    public static void reschedule(TimerQueue queue, String roomCode, GameState before, GameState after) {
        Map<GameTimer, Instant> old = of(roomCode, before);
        Map<GameTimer, Instant> current = of(roomCode, after);
        old.keySet().stream().filter(timer -> !current.containsKey(timer)).forEach(queue::cancel);
        current.forEach((timer, at) -> {
            if (!at.equals(old.get(timer))) {
                queue.schedule(timer, at);
            }
        });
    }
}

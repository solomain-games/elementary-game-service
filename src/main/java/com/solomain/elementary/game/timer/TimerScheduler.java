package com.solomain.elementary.game.timer;

import com.solomain.elementary.game.game.GameRepository;
import com.solomain.elementary.game.realtime.RoomPresenceListener;
import com.solomain.elementary.game.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Раз в секунду забирает из очереди наступившие таймеры и выполняет их: команды ядру —
 * через {@link RoomService}, проверку присутствия — через {@link RoomPresenceListener}.
 * Сроки лежат в Redis, поэтому после перезапуска сервиса таймеры продолжают срабатывать:
 * пропущенные за время простоя сработают при первом же проходе.
 */
@Component
@ConditionalOnProperty(name = "elementary.timers.enabled", havingValue = "true", matchIfMissing = true)
public class TimerScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimerScheduler.class);

    /** Сколько таймеров забирать за раз; если наступило больше — заберём следующую порцию сразу. */
    static final int BATCH = 100;

    private final TimerQueue queue;
    private final RoomService rooms;
    private final RoomPresenceListener presence;
    private final GameRepository games;
    private final TimersProperties properties;
    private final Clock clock;

    public TimerScheduler(TimerQueue queue, RoomService rooms, RoomPresenceListener presence, GameRepository games,
                          TimersProperties properties, Clock clock) {
        this.queue = queue;
        this.rooms = rooms;
        this.presence = presence;
        this.games = games;
        this.properties = properties;
        this.clock = clock;
    }

    /** {@code fixedDelay}: следующий проход — через секунду после окончания предыдущего, проходы не накладываются. */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void fireDue() {
        List<GameTimer> due;
        do {
            due = queue.pollDue(clock.instant(), BATCH);
            due.forEach(this::fire);
        } while (due.size() == BATCH);
    }

    void fire(GameTimer timer) {
        try {
            switch (timer) {
                case GameTimer.PresenceCheck check -> presence.checkPresence(check.roomCode());
                default -> rooms.timerExpired(timer);
            }
        } catch (RuntimeException e) {
            // Таймер уже забран из очереди; повторять не будем — следующий ход или голосование поставят новый
            log.warn("Timer {} failed", timer.key(), e);
        }
    }

    /**
     * После перезапуска сервис не знает, кто в сети: клиенты переподключаются сами. Тем, кто не
     * вернётся, через {@code reconnect-grace} проверка присутствия выставит статус «отключён».
     */
    @EventListener(ApplicationReadyEvent.class)
    public void afterStartup() {
        Instant at = clock.instant().plus(properties.reconnectGrace());
        Set<String> codes = games.codes();
        codes.forEach(code -> queue.schedule(new GameTimer.PresenceCheck(code), at));
        log.info("Presence check scheduled for {} game(s) at {}", codes.size(), at);
    }
}

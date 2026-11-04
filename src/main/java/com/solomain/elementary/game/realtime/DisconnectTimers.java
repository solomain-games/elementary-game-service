package com.solomain.elementary.game.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Таймеры ожидания отключившихся игроков: один на пару «комната + игрок».
 * Повторный запуск заменяет прежний таймер: отключился, вернулся, снова отключился — снова 2 минуты.
 *
 * <p>Пока таймеры живут в памяти процесса и пропадают при перезапуске сервиса.
 * В Г9 сроки переедут в Redis.
 */
@Component
@EnableConfigurationProperties(PresenceProperties.class)
public class DisconnectTimers implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DisconnectTimers.class);

    private final Duration timeout;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("disconnect-timer").daemon().factory());
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public DisconnectTimers(PresenceProperties properties) {
        this.timeout = properties.disconnectTimeout();
    }

    /** Запускает (или перезапускает) ожидание; по истечении выполнится {@code onExpire}. */
    public void start(String code, String playerId, Runnable onExpire) {
        String key = key(code, playerId);
        var timer = new Pending(key, onExpire);
        timer.future = executor.schedule(timer, timeout.toMillis(), TimeUnit.MILLISECONDS);
        Pending previous = pending.put(key, timer);
        if (previous != null) {
            previous.future.cancel(false);
        }
    }

    /** Игрок вернулся — ждать больше нечего. Отменять несуществующий таймер — не ошибка. */
    public void cancel(String code, String playerId) {
        Pending timer = pending.remove(key(code, playerId));
        if (timer != null) {
            timer.future.cancel(false);
        }
    }

    public boolean isPending(String code, String playerId) {
        return pending.containsKey(key(code, playerId));
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private static String key(String code, String playerId) {
        return code + "/" + playerId;
    }

    private final class Pending implements Runnable {

        private final String key;
        private final Runnable onExpire;
        private volatile ScheduledFuture<?> future;

        private Pending(String key, Runnable onExpire) {
            this.key = key;
            this.onExpire = onExpire;
        }

        @Override
        public void run() {
            pending.remove(key, this);   // удаляем только себя: таймер могли уже заменить новым
            try {
                onExpire.run();
            } catch (RuntimeException e) {
                // Исключение убило бы только эту задачу, но без лога мы бы о нём не узнали
                log.warn("Disconnect timer {} failed", key, e);
            }
        }
    }
}

package com.solomain.elementary.game.realtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Таймеры ожидания отключившихся")
class DisconnectTimersTest {

    private final DisconnectTimers timers = new DisconnectTimers(new PresenceProperties(Duration.ofMillis(200)));

    @AfterEach
    void stop() {
        timers.destroy();
    }

    @Test
    @DisplayName("срабатывает по истечении срока и больше не числится ожидающим")
    void fires() throws InterruptedException {
        var fired = new CountDownLatch(1);
        timers.start("K7Q2PX", "anna", fired::countDown);
        assertThat(timers.isPending("K7Q2PX", "anna")).isTrue();

        assertThat(fired.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);   // запись удаляется в том же потоке таймера, сразу перед вызовом
        assertThat(timers.isPending("K7Q2PX", "anna")).isFalse();
    }

    @Test
    @DisplayName("отменённый не срабатывает; отменить несуществующий — не ошибка")
    void cancel() throws InterruptedException {
        var fired = new AtomicInteger();
        timers.start("K7Q2PX", "anna", fired::incrementAndGet);
        timers.cancel("K7Q2PX", "anna");
        timers.cancel("K7Q2PX", "boris");

        Thread.sleep(400);
        assertThat(fired).hasValue(0);
        assertThat(timers.isPending("K7Q2PX", "anna")).isFalse();
    }

    @Test
    @DisplayName("повторный запуск заменяет прежний таймер: срабатывает один раз, новый")
    void restart() throws InterruptedException {
        var first = new AtomicInteger();
        var second = new CountDownLatch(1);
        timers.start("K7Q2PX", "anna", first::incrementAndGet);
        timers.start("K7Q2PX", "anna", second::countDown);

        assertThat(second.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(first).hasValue(0);
    }

    @Test
    @DisplayName("таймеры разных игроков и комнат независимы")
    void independent() throws InterruptedException {
        var fired = new CountDownLatch(1);
        timers.start("K7Q2PX", "anna", fired::countDown);
        timers.start("K7Q2PX", "boris", () -> { });
        timers.cancel("K7Q2PX", "boris");

        assertThat(fired.await(2, TimeUnit.SECONDS)).isTrue();
    }
}

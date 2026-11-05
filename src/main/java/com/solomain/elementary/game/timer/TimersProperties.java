package com.solomain.elementary.game.timer;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки таймеров.
 *
 * @param enabled           запускать ли планировщик ({@link TimerScheduler}); в тестах выключен,
 *                          чтобы он не ходил в Redis, которого там нет
 * @param disconnectTimeout сколько ждём отключившегося посреди партии игрока; не вернулся —
 *                          для партии это выход (ТЗ 4.5, 2 минуты)
 * @param reconnectGrace    сколько даём игрокам на подключение, прежде чем проверить, кто в партии
 *                          так и не появился в сети: после старта партии и после перезапуска сервиса
 */
@Validated
@ConfigurationProperties(prefix = "elementary.timers")
public record TimersProperties(boolean enabled, @NotNull Duration disconnectTimeout, @NotNull Duration reconnectGrace) {
}

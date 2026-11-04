package com.solomain.elementary.game.realtime;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки подключений.
 *
 * @param disconnectTimeout сколько ждём отключившегося посреди партии игрока; не вернулся —
 *                          для партии это выход (ТЗ 4.5, по умолчанию 2 минуты)
 */
@Validated
@ConfigurationProperties(prefix = "elementary.presence")
public record PresenceProperties(@NotNull Duration disconnectTimeout) {
}

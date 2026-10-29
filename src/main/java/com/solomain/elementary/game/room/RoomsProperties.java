package com.solomain.elementary.game.room;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Настройки комнат.
 *
 * @param ttl сколько хранится комната без изменений; каждое изменение продлевает срок.
 *            Так заброшенные комнаты удаляются сами
 */
@Validated
@ConfigurationProperties(prefix = "elementary.rooms")
public record RoomsProperties(@NotNull Duration ttl) {
}

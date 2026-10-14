package com.solomain.elementary.game.cases;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Настройки дел.
 *
 * @param dir папка с делами: в ней по подпапке на дело, в каждой {@code case.json}
 */
@Validated
@ConfigurationProperties(prefix = "elementary.cases")
public record CasesProperties(@NotNull Path dir) {
}

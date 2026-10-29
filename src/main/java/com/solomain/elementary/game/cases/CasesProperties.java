package com.solomain.elementary.game.cases;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Настройки дел.
 *
 * <p>Путь хранится строкой, а не {@link Path}: при привязке к {@code Path} Spring сначала
 * пытается найти строку как ресурс в classpath, и относительный путь вроде
 * {@code ../cases/cases} превращается в ошибку. Строка же превращается в путь файловой
 * системы явно, в {@link #path()}.
 *
 * @param dir папка с делами (относительно папки, из которой запущен сервис, или абсолютная):
 *            в ней по подпапке на дело, в каждой {@code case.json}
 */
@Validated
@ConfigurationProperties(prefix = "elementary.cases")
public record CasesProperties(@NotBlank String dir) {

    /** Папка с делами как абсолютный путь. */
    public Path path() {
        return Path.of(dir).toAbsolutePath().normalize();
    }
}

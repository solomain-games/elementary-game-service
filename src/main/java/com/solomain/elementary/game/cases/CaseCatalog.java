package com.solomain.elementary.game.cases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Все дела, доступные для игры. Загружаются один раз при старте сервиса из папки
 * {@code elementary.cases.dir}: каждая подпапка с {@code case.json} — одно дело.
 *
 * <p>Дело с ошибками пропускается (с подробным сообщением в логе), остальные работают:
 * одно сломанное дело не должно останавливать всю игру. А вот отсутствие самой папки —
 * ошибка настройки, и сервис не запускается.
 */
@Component
public class CaseCatalog {

    private static final Logger log = LoggerFactory.getLogger(CaseCatalog.class);

    /** id → дело, в порядке id. */
    private final Map<String, LoadedCase> cases;

    public CaseCatalog(CasesProperties properties) {
        this.cases = loadAll(properties.dir().toAbsolutePath().normalize(), new CaseLoader());
    }

    /** Описания всех дел для выбора в комнате, в порядке id. */
    public List<CaseSummary> summaries() {
        return cases.values().stream().map(LoadedCase::summary).toList();
    }

    public Optional<LoadedCase> find(String caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    private static Map<String, LoadedCase> loadAll(Path dir, CaseLoader loader) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Cases directory not found: " + dir + " (check elementary.cases.dir)");
        }
        Map<String, LoadedCase> loaded = new LinkedHashMap<>();
        for (Path caseDir : caseDirs(dir)) {
            try {
                LoadedCase loadedCase = loader.load(caseDir);
                loaded.put(loadedCase.id(), loadedCase);
                log.info("Loaded case '{}': {} cards, {} questions", loadedCase.id(),
                        loadedCase.definition().cards().size(), loadedCase.definition().questions().size());
            } catch (InvalidCaseException e) {
                log.error("Skipped case: {}", e.getMessage());
            }
        }
        if (loaded.isEmpty()) {
            log.warn("No cases loaded from {}", dir);
        } else {
            log.info("Loaded {} case(s) from {}", loaded.size(), dir);
        }
        return Collections.unmodifiableMap(loaded);
    }

    /** Подпапки с файлом case.json, по алфавиту; скрытые (.git и т. п.) пропускаются. */
    private static List<Path> caseDirs(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(child -> !child.getFileName().toString().startsWith("."))
                    .filter(child -> Files.isRegularFile(child.resolve(CaseLoader.CASE_FILE)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list cases directory " + dir, e);
        }
    }
}

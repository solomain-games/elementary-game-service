package com.solomain.elementary.game.cases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Копии дела-заглушки во временной папке теста: их можно портить, не трогая оригинал. */
final class TestCaseDirs {

    private TestCaseDirs() {
    }

    /** Копирует дело demo из test-cases в {@code root/folderName} и возвращает путь к копии. */
    static Path copyDemo(Path root, String folderName) {
        try {
            Path source = Path.of(TestCaseDirs.class.getResource("/test-cases/demo").toURI());
            Path target = root.resolve(folderName);
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.toList()) {
                    Path copy = target.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy);
                    }
                }
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static Path copyDemo(Path root) {
        return copyDemo(root, "demo");
    }

    /** Заменяет текст в case.json: так тест портит дело ровно в одном месте. */
    static void edit(Path caseDir, String from, String to) {
        Path file = caseDir.resolve(CaseLoader.CASE_FILE);
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (!json.contains(from)) {
                throw new IllegalArgumentException("case.json does not contain: " + from);
            }
            Files.writeString(file, json.replace(from, to), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

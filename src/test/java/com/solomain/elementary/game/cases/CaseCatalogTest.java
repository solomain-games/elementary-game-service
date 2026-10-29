package com.solomain.elementary.game.cases;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.solomain.elementary.game.cases.TestCaseDirs.copyDemo;
import static com.solomain.elementary.game.cases.TestCaseDirs.edit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Каталог дел")
class CaseCatalogTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("загружает дела из подпапок")
    void loadsCases() {
        copyDemo(root);

        var catalog = new CaseCatalog(new CasesProperties(root.toString()));

        assertThat(catalog.summaries()).extracting(CaseSummary::id).containsExactly("demo");
        assertThat(catalog.find("demo")).isPresent();
        assertThat(catalog.find("unknown")).isEmpty();
    }

    @Test
    @DisplayName("сломанное дело пропускается, остальные работают")
    void skipsInvalidCase() throws IOException {
        copyDemo(root);
        Path broken = copyDemo(root, "broken");   // id внутри — demo, не совпадает с папкой
        edit(broken, "\"durationMinutes\": 10", "\"durationMinutes\": 0");
        Files.createDirectories(root.resolve("not-a-case"));   // папка без case.json — не дело

        var catalog = new CaseCatalog(new CasesProperties(root.toString()));

        assertThat(catalog.summaries()).extracting(CaseSummary::id).containsExactly("demo");
    }

    @Test
    @DisplayName("нет папки с делами — сервис не запускается")
    void missingDirectory() {
        assertThatThrownBy(() -> new CaseCatalog(new CasesProperties(root.resolve("missing").toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cases directory not found");
    }
}

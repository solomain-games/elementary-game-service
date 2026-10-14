package com.solomain.elementary.game.cases;

import com.solomain.elementary.core.model.ImageCard;
import com.solomain.elementary.core.model.TextAlign;
import com.solomain.elementary.core.model.TextCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.solomain.elementary.game.cases.TestCaseDirs.copyDemo;
import static com.solomain.elementary.game.cases.TestCaseDirs.edit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Загрузка дела")
class CaseLoaderTest {

    private final CaseLoader loader = new CaseLoader();

    @TempDir
    Path root;

    @Nested
    @DisplayName("дело-заглушка")
    class Demo {

        @Test
        @DisplayName("превращается в модель ядра")
        void definition() {
            LoadedCase loaded = loader.load(copyDemo(root));

            var definition = loaded.definition();
            assertThat(definition.id()).isEqualTo("demo");
            assertThat(definition.cards()).hasSize(5);
            assertThat(definition.questions()).hasSize(2);
            assertThat(definition.questions().getFirst().correctOptionId()).isEqualTo("guest");
            assertThat(definition.solution().values().get("ru")).contains("[[1]]");
        }

        @Test
        @DisplayName("карты обоих типов со всеми полями")
        void cards() {
            var definition = loader.load(copyDemo(root)).definition();

            assertThat(definition.card(1)).isInstanceOfSatisfying(ImageCard.class, card -> {
                assertThat(card.image()).isEqualTo("cards/01.webp");
                assertThat(card.caption().values()).isEqualTo(Map.of("ru", "Пустая шкатулка в спальне"));
                assertThat(card.relevant()).isTrue();
            });
            assertThat(definition.card(3).relevant()).isFalse();
            assertThat(definition.card(4)).isInstanceOfSatisfying(TextCard.class, card -> {
                assertThat(card.background()).isEqualTo("backgrounds/paper.webp");
                assertThat(card.align()).isEqualTo(TextAlign.CENTER);
            });
            // align не задан — по умолчанию LEFT
            assertThat(definition.card(2)).isInstanceOfSatisfying(TextCard.class,
                    card -> assertThat(card.align()).isEqualTo(TextAlign.LEFT));
        }

        @Test
        @DisplayName("описание для списка и предыстория")
        void summaryAndPrologue() {
            LoadedCase loaded = loader.load(copyDemo(root));

            CaseSummary summary = loaded.summary();
            assertThat(summary.title()).isEqualTo(Map.of("ru", "Пропавший браслет"));
            assertThat(summary.cover()).isEqualTo("images/cover.webp");
            assertThat(summary.languages()).containsExactly("ru");
            assertThat(summary.recommendedPlayers()).isEqualTo(new CaseSummary.PlayersRange(1, 4));
            assertThat(summary.durationMinutes()).isEqualTo(10);
            assertThat(loaded.supportsLanguage("ru")).isTrue();
            assertThat(loaded.supportsLanguage("en")).isFalse();

            assertThat(loaded.prologue().slides()).hasSize(2);
            assertThat(loaded.prologue().slides().getFirst().image()).isEqualTo("images/prologue-1.webp");
            assertThat(loaded.prologue().slides().get(1).image()).isNull();
        }
    }

    @Nested
    @DisplayName("ошибки в деле")
    class Errors {

        @Test
        @DisplayName("id не совпадает с именем папки")
        void folderMismatch() {
            assertProblems(copyDemo(root, "other"), "id: 'demo' does not match folder name 'other'");
        }

        @Test
        @DisplayName("язык объявлен, а переводов нет — ошибка у каждого текста")
        void missingTranslation() {
            Path dir = copyDemo(root);
            edit(dir, "\"ru\"\n  ]", "\"ru\", \"en\"\n  ]");

            assertThatThrownBy(() -> loader.load(dir))
                    .isInstanceOfSatisfying(InvalidCaseException.class, e -> assertThat(e.problems())
                            .contains("title: no 'en' translation", "cards[1] (#2).text: no 'en' translation",
                                    "solution: no 'en' translation")
                            .hasSizeGreaterThan(10));
        }

        @Test
        @DisplayName("файл не найден")
        void missingFile() {
            Path dir = copyDemo(root);
            edit(dir, "cards/03.webp", "cards/99.webp");

            assertProblems(dir, "cards[2] (#3).image: file not found: cards/99.webp");
        }

        @Test
        @DisplayName("путь за пределы папки дела")
        void pathTraversal() {
            Path dir = copyDemo(root);
            edit(dir, "images/cover.webp", "../secret.webp");

            assertProblems(dir, "cover: invalid path '../secret.webp' (relative, without '..')");
        }

        @Test
        @DisplayName("ссылка на несуществующую карту")
        void brokenCardLink() {
            Path dir = copyDemo(root);
            edit(dir, "([[4]])", "([[42]])");

            assertProblems(dir, "solution.ru: link [[42]] to a missing card");
        }

        @Test
        @DisplayName("правильного ответа нет среди вариантов")
        void correctNotAnOption() {
            Path dir = copyDemo(root);
            edit(dir, "\"correct\": \"guest\"", "\"correct\": \"butler\"");

            assertProblems(dir, "questions[0].correct: 'butler' is not one of the options");
        }

        @Test
        @DisplayName("повторяющийся номер карты")
        void duplicateNumber() {
            Path dir = copyDemo(root);
            edit(dir, "\"number\": 3", "\"number\": 2");

            // Карты №3 больше нет — и ссылка на неё в истинной картине тоже ошибка
            assertProblems(dir, "cards[2] (#2).number: duplicate card number",
                    "solution.ru: link [[3]] to a missing card");
        }

        @Test
        @DisplayName("нет стартовой карты №1")
        void noStartingCard() {
            Path dir = copyDemo(root);
            edit(dir, "\"number\": 1,", "\"number\": 6,");
            edit(dir, "шкатулки ([[1]])", "шкатулки");

            assertProblems(dir, "cards: starting card #1 is missing");
        }

        @Test
        @DisplayName("неверное выравнивание")
        void invalidAlign() {
            Path dir = copyDemo(root);
            edit(dir, "\"align\": \"center\"", "\"align\": \"middle\"");

            assertThatThrownBy(() -> loader.load(dir))
                    .isInstanceOfSatisfying(InvalidCaseException.class, e -> assertThat(e.problems())
                            .anyMatch(problem -> problem.startsWith("cards[3] (#4).align: must be one of")));
        }

        @Test
        @DisplayName("все ошибки сообщаются сразу")
        void allProblemsAtOnce() {
            Path dir = copyDemo(root);
            edit(dir, "\"durationMinutes\": 10", "\"durationMinutes\": 0");
            edit(dir, "\"access\": \"free\"", "\"access\": \"vip\"");

            assertThatThrownBy(() -> loader.load(dir))
                    .isInstanceOfSatisfying(InvalidCaseException.class, e -> assertThat(e.problems()).hasSize(2))
                    .hasMessageContaining("durationMinutes")
                    .hasMessageContaining("access");
        }

        @Test
        @DisplayName("опечатка в имени поля — ошибка, а не молча пропущенное значение")
        void unknownField() {
            Path dir = copyDemo(root);
            edit(dir, "\"caption\"", "\"captoin\"");

            assertThatThrownBy(() -> loader.load(dir))
                    .isInstanceOf(InvalidCaseException.class)
                    .hasMessageContaining("case.json is malformed")
                    .hasMessageContaining("captoin");
        }

        @Test
        @DisplayName("неизвестный тип карты")
        void unknownCardType() {
            Path dir = copyDemo(root);
            edit(dir, "\"type\": \"image\"", "\"type\": \"video\"");

            assertThatThrownBy(() -> loader.load(dir))
                    .isInstanceOf(InvalidCaseException.class)
                    .hasMessageContaining("case.json is malformed");
        }

        @Test
        @DisplayName("нет case.json")
        void noFile() {
            assertThatThrownBy(() -> loader.load(root.resolve("nothing")))
                    .isInstanceOf(InvalidCaseException.class)
                    .hasMessageContaining("cannot read case.json");
        }

        private void assertProblems(Path dir, String... expected) {
            assertThatThrownBy(() -> loader.load(dir))
                    .isInstanceOfSatisfying(InvalidCaseException.class,
                            e -> assertThat(e.problems()).isEqualTo(List.of(expected)));
        }
    }
}

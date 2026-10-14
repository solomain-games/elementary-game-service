package com.solomain.elementary.game.cases;

import com.solomain.elementary.core.model.AnswerOption;
import com.solomain.elementary.core.model.Card;
import com.solomain.elementary.core.model.CaseDefinition;
import com.solomain.elementary.core.model.ImageCard;
import com.solomain.elementary.core.model.LocalizedText;
import com.solomain.elementary.core.model.Question;
import com.solomain.elementary.core.model.TextAlign;
import com.solomain.elementary.core.model.TextCard;
import com.solomain.elementary.game.cases.CaseFile.CardFile;
import com.solomain.elementary.game.cases.CaseFile.ImageCardFile;
import com.solomain.elementary.game.cases.CaseFile.OptionFile;
import com.solomain.elementary.game.cases.CaseFile.QuestionFile;
import com.solomain.elementary.game.cases.CaseFile.SlideFile;
import com.solomain.elementary.game.cases.CaseFile.TextCardFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Читает дело из папки и проверяет его. Проверки повторяют JSON Schema репозитория cases
 * и добавляют то, что схема выразить не может: файлы существуют, имя папки совпадает с {@code id},
 * все тексты переведены на все объявленные языки, ссылки {@code [[15]]} ведут на существующие карты.
 *
 * <p>Работает в два шага: сначала собирает все ошибки (чтобы автор дела увидел их сразу),
 * и только если их нет — строит модель ядра {@link CaseDefinition}.
 */
public final class CaseLoader {

    /** Имя файла дела внутри его папки. */
    public static final String CASE_FILE = "case.json";

    private static final int FORMAT_VERSION = 1;
    private static final Pattern CASE_ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2}$");
    private static final Pattern QUESTION_ID = Pattern.compile("^[a-z0-9-]+$");
    /** Относительный путь без ведущего / и без .. — файл не может оказаться вне папки дела. */
    private static final Pattern PATH = Pattern.compile("^(?!/)(?!.*\\.\\.)[A-Za-z0-9._/-]+$");
    private static final Pattern CARD_REF = Pattern.compile("\\[\\[(\\d+)]]");
    private static final Set<String> ACCESS = Set.of("free", "paid");
    private static final Set<String> ALIGN = Set.of("left", "center", "right", "justify");
    private static final int MAX_PLAYERS = 8;

    /** Строгий разбор: опечатка в имени поля — ошибка, а не молча пропущенное значение. */
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Загружает дело из папки {@code caseDir}.
     *
     * @throws InvalidCaseException если файла нет, он не разбирается или дело не прошло проверку
     */
    public LoadedCase load(Path caseDir) {
        return new Validation(caseDir, read(caseDir)).toLoadedCase();
    }

    private CaseFile read(Path caseDir) {
        try (InputStream in = Files.newInputStream(caseDir.resolve(CASE_FILE))) {
            return json.readValue(in, CaseFile.class);
        } catch (IOException e) {
            throw new InvalidCaseException(caseDir, List.of("cannot read " + CASE_FILE + ": " + e));
        } catch (JacksonException e) {
            // Сообщение Jackson содержит и причину, и место в файле (строка, столбец, путь к полю)
            throw new InvalidCaseException(caseDir, List.of(CASE_FILE + " is malformed: " + e.getMessage()));
        }
    }

    /** Проверка одного дела: копит ошибки в {@link #problems}. */
    private static final class Validation {

        private final Path caseDir;
        private final CaseFile file;
        private final List<String> problems = new ArrayList<>();
        private final Set<String> languages;
        private final Set<Integer> cardNumbers;

        Validation(Path caseDir, CaseFile file) {
            this.caseDir = caseDir;
            this.file = file;
            this.languages = file.languages() == null ? Set.of() : new LinkedHashSet<>(file.languages());
            this.cardNumbers = file.cards() == null ? Set.of() : file.cards().stream()
                    .map(CardFile::number)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        LoadedCase toLoadedCase() {
            checkHeader();
            checkPrologue();
            checkCards();
            checkQuestions();
            text(file.solution(), "solution", true);
            if (!problems.isEmpty()) {
                throw new InvalidCaseException(caseDir, problems);
            }
            // Все поля на месте: остальные правила (уникальность id вопросов и т. п.) проверит ядро
            try {
                var definition = new CaseDefinition(file.id(),
                        file.cards().stream().map(Validation::toCard).toList(),
                        file.questions().stream().map(Validation::toQuestion).toList(),
                        localized(file.solution()));
                return new LoadedCase(definition, toSummary(), toPrologue());
            } catch (IllegalArgumentException e) {
                throw new InvalidCaseException(caseDir, List.of(e.getMessage()));
            }
        }

        // ---------- проверки ----------

        private void checkHeader() {
            if (!Objects.equals(file.formatVersion(), FORMAT_VERSION)) {
                problems.add("formatVersion: expected " + FORMAT_VERSION + ", got " + file.formatVersion());
            }
            String folder = caseDir.getFileName().toString();
            if (required(file.id(), "id")) {
                if (!CASE_ID.matcher(file.id()).matches()) {
                    problems.add("id: '" + file.id() + "' must be lowercase latin letters, digits and dashes");
                } else if (!file.id().equals(folder)) {
                    problems.add("id: '" + file.id() + "' does not match folder name '" + folder + "'");
                }
            }
            if (required(file.languages(), "languages")) {
                if (languages.isEmpty()) {
                    problems.add("languages: at least one language is required");
                }
                if (languages.size() != file.languages().size()) {
                    problems.add("languages: duplicate language codes");
                }
                languages.stream()
                        .filter(language -> language == null || !LANGUAGE.matcher(language).matches())
                        .forEach(language -> problems.add("languages: invalid code '" + language + "'"));
            }
            text(file.title(), "title", true);
            text(file.summary(), "summary", true);
            path(file.cover(), "cover", true);
            if (required(file.access(), "access") && !ACCESS.contains(file.access())) {
                problems.add("access: must be one of " + ACCESS + ", got '" + file.access() + "'");
            }
            if (required(file.recommendedPlayers(), "recommendedPlayers")) {
                Integer min = file.recommendedPlayers().min();
                Integer max = file.recommendedPlayers().max();
                if (required(min, "recommendedPlayers.min") && required(max, "recommendedPlayers.max")
                        && (min < 1 || max > MAX_PLAYERS || min > max)) {
                    problems.add("recommendedPlayers: expected 1 <= min <= max <= " + MAX_PLAYERS
                            + ", got " + min + ".." + max);
                }
            }
            if (required(file.durationMinutes(), "durationMinutes") && file.durationMinutes() < 1) {
                problems.add("durationMinutes: must be positive");
            }
        }

        private void checkPrologue() {
            if (!required(file.prologue(), "prologue") || !required(file.prologue().slides(), "prologue.slides")) {
                return;
            }
            List<SlideFile> slides = file.prologue().slides();
            if (slides.isEmpty()) {
                problems.add("prologue.slides: at least one slide is required");
            }
            for (int i = 0; i < slides.size(); i++) {
                SlideFile slide = slides.get(i);
                String field = "prologue.slides[" + i + "]";
                path(slide.image(), field + ".image", false);
                text(slide.text(), field + ".text", false);
                localizedPath(slide.audio(), field + ".audio");
                localizedPath(slide.video(), field + ".video");
                if (slide.image() == null && slide.text() == null && slide.video() == null) {
                    problems.add(field + ": needs image, text or video");
                }
                if (slide.durationSeconds() == null && slide.audio() == null && slide.video() == null) {
                    problems.add(field + ": needs durationSeconds, audio or video");
                }
                if (slide.durationSeconds() != null && slide.durationSeconds() <= 0) {
                    problems.add(field + ".durationSeconds: must be positive");
                }
            }
        }

        private void checkCards() {
            if (!required(file.cards(), "cards")) {
                return;
            }
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < file.cards().size(); i++) {
                CardFile card = file.cards().get(i);
                String field = "cards[" + i + "]";
                if (card == null) {
                    problems.add(field + ": is null");
                    continue;
                }
                if (required(card.number(), field + ".number")) {
                    field = field + " (#" + card.number() + ")";
                    if (card.number() < 1) {
                        problems.add(field + ".number: must be >= 1");
                    } else if (!seen.add(card.number())) {
                        problems.add(field + ".number: duplicate card number");
                    }
                }
                required(card.relevant(), field + ".relevant");
                switch (card) {
                    case ImageCardFile image -> {
                        path(image.image(), field + ".image", true);
                        text(image.caption(), field + ".caption", false);
                    }
                    case TextCardFile textCard -> {
                        path(textCard.background(), field + ".background", true);
                        text(textCard.text(), field + ".text", true);
                        if (textCard.align() != null && !ALIGN.contains(textCard.align())) {
                            problems.add(field + ".align: must be one of " + ALIGN
                                    + ", got '" + textCard.align() + "'");
                        }
                    }
                }
            }
            if (!seen.contains(1)) {
                problems.add("cards: starting card #1 is missing");
            }
        }

        private void checkQuestions() {
            if (!required(file.questions(), "questions")) {
                return;
            }
            if (file.questions().isEmpty()) {
                problems.add("questions: at least one question is required");
            }
            for (int i = 0; i < file.questions().size(); i++) {
                QuestionFile question = file.questions().get(i);
                String field = "questions[" + i + "]";
                if (required(question.id(), field + ".id") && !QUESTION_ID.matcher(question.id()).matches()) {
                    problems.add(field + ".id: '" + question.id() + "' must be lowercase latin letters, digits and dashes");
                }
                text(question.text(), field + ".text", true);
                if (!required(question.options(), field + ".options")) {
                    continue;
                }
                Set<String> optionIds = new HashSet<>();
                for (int j = 0; j < question.options().size(); j++) {
                    OptionFile option = question.options().get(j);
                    String optionField = field + ".options[" + j + "]";
                    if (required(option.id(), optionField + ".id")) {
                        optionIds.add(option.id());
                    }
                    text(option.text(), optionField + ".text", true);
                }
                if (required(question.correct(), field + ".correct") && !optionIds.contains(question.correct())) {
                    problems.add(field + ".correct: '" + question.correct() + "' is not one of the options");
                }
            }
        }

        /** Поле задано; иначе запоминает ошибку. */
        private boolean required(Object value, String field) {
            if (value == null) {
                problems.add(field + ": is required");
                return false;
            }
            return true;
        }

        /** Текст: есть перевод на каждый объявленный язык, лишних языков нет, ссылки на карты верные. */
        private void text(Map<String, String> text, String field, boolean isRequired) {
            if (text == null) {
                if (isRequired) {
                    problems.add(field + ": is required");
                }
                return;
            }
            checkLanguages(text, field);
            text.forEach((language, value) -> {
                if (value == null || value.isBlank()) {
                    problems.add(field + "." + language + ": is empty");
                    return;
                }
                Matcher refs = CARD_REF.matcher(value);
                while (refs.find()) {
                    int number = Integer.parseInt(refs.group(1));
                    if (!cardNumbers.contains(number)) {
                        problems.add(field + "." + language + ": link [[" + number + "]] to a missing card");
                    }
                }
            });
        }

        /** Файл, свой для каждого языка (озвучка, видео). */
        private void localizedPath(Map<String, String> paths, String field) {
            if (paths == null) {
                return;
            }
            checkLanguages(paths, field);
            paths.forEach((language, path) -> path(path, field + "." + language, true));
        }

        private void checkLanguages(Map<String, String> values, String field) {
            for (String language : languages) {
                if (!values.containsKey(language)) {
                    problems.add(field + ": no '" + language + "' translation");
                }
            }
            for (String language : values.keySet()) {
                if (!languages.contains(language)) {
                    problems.add(field + ": language '" + language + "' is not listed in languages");
                }
            }
        }

        /** Путь к файлу дела: безопасный и файл существует. */
        private void path(String path, String field, boolean isRequired) {
            if (path == null) {
                if (isRequired) {
                    problems.add(field + ": is required");
                }
                return;
            }
            if (!PATH.matcher(path).matches()) {
                problems.add(field + ": invalid path '" + path + "' (relative, without '..')");
            } else if (!Files.isRegularFile(caseDir.resolve(path))) {
                problems.add(field + ": file not found: " + path);
            }
        }

        // ---------- построение модели (поля уже проверены) ----------

        private static Card toCard(CardFile card) {
            return switch (card) {
                case ImageCardFile image ->
                        new ImageCard(image.number(), image.relevant(), image.image(), localized(image.caption()));
                case TextCardFile text -> new TextCard(text.number(), text.relevant(), localized(text.text()),
                        text.background(),
                        text.align() == null ? TextAlign.LEFT : TextAlign.valueOf(text.align().toUpperCase(Locale.ROOT)));
            };
        }

        private static Question toQuestion(QuestionFile question) {
            List<AnswerOption> options = question.options().stream()
                    .map(option -> new AnswerOption(option.id(), localized(option.text())))
                    .toList();
            return new Question(question.id(), localized(question.text()), options, question.correct());
        }

        private CaseSummary toSummary() {
            return new CaseSummary(file.id(), file.title(), file.summary(), file.cover(), file.languages(),
                    new CaseSummary.PlayersRange(file.recommendedPlayers().min(), file.recommendedPlayers().max()),
                    file.durationMinutes());
        }

        private Prologue toPrologue() {
            return new Prologue(file.prologue().slides().stream()
                    .map(slide -> new Prologue.Slide(slide.image(), slide.text(), slide.audio(), slide.video(),
                            slide.durationSeconds()))
                    .toList());
        }

        private static LocalizedText localized(Map<String, String> text) {
            return text == null ? null : new LocalizedText(text);
        }
    }
}

package com.solomain.elementary.game.cases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * Файл {@code case.json} как он есть (формат описан в README репозитория cases и в
 * {@code schema/case.schema.json}). Только чтение: проверки и превращение в модель ядра —
 * в {@link CaseLoader}.
 *
 * <p>Обёртки ({@code Integer}, {@code Boolean}) вместо примитивов — чтобы отличить
 * «поле не указано» ({@code null}) от нуля и {@code false}.
 */
@JsonIgnoreProperties({"$schema"})
record CaseFile(
        Integer formatVersion,
        String id,
        List<String> languages,
        Map<String, String> title,
        Map<String, String> summary,
        String cover,
        String access,
        PlayersRange recommendedPlayers,
        Integer durationMinutes,
        PrologueFile prologue,
        List<CardFile> cards,
        List<QuestionFile> questions,
        Map<String, String> solution) {

    record PlayersRange(Integer min, Integer max) {
    }

    record PrologueFile(List<SlideFile> slides) {
    }

    record SlideFile(
            String image,
            Map<String, String> text,
            Map<String, String> audio,
            Map<String, String> video,
            Double durationSeconds) {
    }

    /** Карта; конкретный тип выбирается по полю {@code "type"}: {@code image} или {@code text}. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ImageCardFile.class, name = "image"),
            @JsonSubTypes.Type(value = TextCardFile.class, name = "text")})
    sealed interface CardFile permits ImageCardFile, TextCardFile {
        Integer number();

        Boolean relevant();
    }

    record ImageCardFile(Integer number, Boolean relevant, String image, Map<String, String> caption)
            implements CardFile {
    }

    record TextCardFile(Integer number, Boolean relevant, String background, Map<String, String> text,
                        String align) implements CardFile {
    }

    record QuestionFile(String id, Map<String, String> text, List<OptionFile> options, String correct) {
    }

    record OptionFile(String id, Map<String, String> text) {
    }
}

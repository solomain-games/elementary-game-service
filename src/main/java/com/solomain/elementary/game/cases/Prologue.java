package com.solomain.elementary.game.cases;

import java.util.List;
import java.util.Map;

/**
 * Предыстория дела (протокол, 4.3). Пока только загружается и проверяется;
 * отдавать её клиентам и показывать синхронно будем в Г8.
 */
public record Prologue(List<Slide> slides) {

    public Prologue {
        slides = List.copyOf(slides);
    }

    /**
     * Слайд. Необязательные поля — {@code null}.
     *
     * @param durationSeconds длительность; {@code null}, если её задаёт аудио или видео
     */
    public record Slide(
            String image,
            Map<String, String> text,
            Map<String, String> audio,
            Map<String, String> video,
            Double durationSeconds) {
    }
}

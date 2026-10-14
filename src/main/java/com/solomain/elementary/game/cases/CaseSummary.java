package com.solomain.elementary.game.cases;

import java.util.List;
import java.util.Map;

/**
 * Дело в списке для выбора (протокол, 4.2). Тексты — объекты переводов {@code {"ru": "..."}}.
 *
 * @param cover путь к обложке относительно папки дела
 */
public record CaseSummary(
        String id,
        Map<String, String> title,
        Map<String, String> summary,
        String cover,
        List<String> languages,
        PlayersRange recommendedPlayers,
        int durationMinutes) {

    public CaseSummary {
        title = Map.copyOf(title);
        summary = Map.copyOf(summary);
        languages = List.copyOf(languages);
    }

    /** Рекомендуемое число игроков. */
    public record PlayersRange(int min, int max) {
    }
}

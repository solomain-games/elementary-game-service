package com.solomain.elementary.game.cases;

import com.solomain.elementary.core.model.CaseDefinition;

/**
 * Загруженное и проверенное дело.
 *
 * @param definition то, что нужно правилам игры (ядро {@code game-core}), — с правильными ответами
 * @param summary    описание для списка дел
 * @param prologue   предыстория
 */
public record LoadedCase(CaseDefinition definition, CaseSummary summary, Prologue prologue) {

    public String id() {
        return definition.id();
    }

    /** Есть ли у дела перевод на этот язык. */
    public boolean supportsLanguage(String language) {
        return summary.languages().contains(language);
    }
}

package com.solomain.elementary.game.room;

import java.util.Map;

/**
 * Открытые сведения о комнате (протокол, 4.5): чтобы показать экран «Войти в комнату» до входа.
 * Участники и их имена сюда не входят — их видят только участники, в снимках.
 */
public record RoomInfo(
        String code,
        String caseId,
        Map<String, String> caseTitle,
        String language,
        int players,
        int maxPlayers,
        RoomStatus status) {
}

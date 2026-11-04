package com.solomain.elementary.game.room;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Событие комнаты для всех её участников (протокол, 5.4): подсказка для анимаций и уведомлений.
 * Истина всё равно приходит снимком. Пустые поля в JSON не попадают: у каждого типа свой набор.
 *
 * @param type        что произошло: {@code PLAYER_JOINED}, {@code CARD_PLAYED}, ...
 * @param playerId    о ком событие; {@code null}, если ни о ком
 * @param name        имя вошедшего (только {@code PLAYER_JOINED})
 * @param cardNumber  выложенная карта (только {@code CARD_PLAYED})
 * @param byTimer     ход сделан по истечении времени (только {@code CARD_PLAYED})
 * @param initiatorId кто начал голосование (только {@code VOTE_STARTED})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomEvent(String type, String playerId, String name, Integer cardNumber, Boolean byTimer,
                        String initiatorId) {

    private static RoomEvent of(String type, String playerId) {
        return new RoomEvent(type, playerId, null, null, null, null);
    }

    public static RoomEvent playerJoined(String playerId, String name) {
        return new RoomEvent("PLAYER_JOINED", playerId, name, null, null, null);
    }

    public static RoomEvent playerLeftRoom(String playerId) {
        return of("PLAYER_LEFT_ROOM", playerId);
    }

    public static RoomEvent playerKicked(String playerId) {
        return of("PLAYER_KICKED", playerId);
    }

    public static RoomEvent gameStarted() {
        return of("GAME_STARTED", null);
    }

    public static RoomEvent cardPlayed(String playerId, int cardNumber, boolean byTimer) {
        return new RoomEvent("CARD_PLAYED", playerId, null, cardNumber, byTimer, null);
    }

    public static RoomEvent cardDiscarded(String playerId) {
        return of("CARD_DISCARDED", playerId);
    }

    public static RoomEvent playerGaveUp(String playerId) {
        return of("PLAYER_GAVE_UP", playerId);
    }

    public static RoomEvent playerDisconnected(String playerId) {
        return of("PLAYER_DISCONNECTED", playerId);
    }

    public static RoomEvent playerReconnected(String playerId) {
        return of("PLAYER_RECONNECTED", playerId);
    }

    public static RoomEvent voteStarted(String initiatorId) {
        return new RoomEvent("VOTE_STARTED", null, null, null, null, initiatorId);
    }

    /** События без полей: {@code VOTE_PASSED}, {@code VOTE_FAILED}, {@code NO_MOVES_LEFT}, {@code RESULTS_SHOWN}. */
    public static RoomEvent simple(String type) {
        return of(type, null);
    }
}

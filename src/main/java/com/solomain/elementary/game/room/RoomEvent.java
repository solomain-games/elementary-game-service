package com.solomain.elementary.game.room;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Событие комнаты для всех её участников (протокол, 5.4): подсказка для анимаций и уведомлений.
 * Истина всё равно приходит снимком. Пустые поля в JSON не попадают: у каждого типа свой набор.
 *
 * @param type     что произошло: {@code PLAYER_JOINED}, {@code PLAYER_LEFT_ROOM}, ...
 * @param playerId о ком событие; {@code null}, если ни о ком
 * @param name     имя вошедшего (только {@code PLAYER_JOINED})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomEvent(String type, String playerId, String name) {

    public static RoomEvent playerJoined(String playerId, String name) {
        return new RoomEvent("PLAYER_JOINED", playerId, name);
    }

    public static RoomEvent playerLeftRoom(String playerId) {
        return new RoomEvent("PLAYER_LEFT_ROOM", playerId, null);
    }

    public static RoomEvent playerKicked(String playerId) {
        return new RoomEvent("PLAYER_KICKED", playerId, null);
    }

    public static RoomEvent gameStarted() {
        return new RoomEvent("GAME_STARTED", null, null);
    }
}

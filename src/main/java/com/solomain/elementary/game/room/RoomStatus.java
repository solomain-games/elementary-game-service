package com.solomain.elementary.game.room;

/** Состояние комнаты (протокол, 4.5). */
public enum RoomStatus {
    /** Зал ожидания: можно входить, выходить, исключать. */
    WAITING,
    /** Партия идёт. */
    IN_GAME,
    /** Партия завершена. */
    FINISHED
}

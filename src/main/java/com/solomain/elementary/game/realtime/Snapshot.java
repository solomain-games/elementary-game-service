package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.RoomStatus;

import java.time.Instant;
import java.util.List;

/**
 * Личный снимок комнаты и партии (протокол, 5.3): всё, что игрок должен видеть.
 * Клиент заменяет им прежнее состояние целиком.
 *
 * @param protocolVersion версия протокола
 * @param version         номер изменения комнаты: снимок с меньшим номером клиент отбрасывает
 * @param serverTime      время сервера — по нему клиент поправляет свои часы для таймеров
 * @param room            комната
 * @param game            представление партии; {@code null} в зале ожидания (партии — в Г7)
 */
public record Snapshot(int protocolVersion, long version, Instant serverTime, RoomView room, Object game) {

    public static final int PROTOCOL_VERSION = 1;

    /**
     * @param members  все участники в порядке входа
     * @param prologue синхронный показ предыстории; {@code null}, пока его нет (Г8)
     */
    public record RoomView(
            String code,
            String caseId,
            String language,
            int maxPlayers,
            Integer turnTimeoutSeconds,
            String hostId,
            RoomStatus status,
            List<MemberView> members,
            Object prologue) {
    }

    /** @param online есть ли у игрока открытое подключение к этой комнате */
    public record MemberView(String id, String name, boolean online) {
    }
}

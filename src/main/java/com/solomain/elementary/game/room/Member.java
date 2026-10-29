package com.solomain.elementary.game.room;

import java.time.Instant;
import java.util.Objects;

/**
 * Участник комнаты.
 *
 * @param id       id игрока из токена
 * @param name     имя из токена на момент входа
 * @param joinedAt когда вошёл
 */
public record Member(String id, String name, Instant joinedAt) {

    public Member {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}

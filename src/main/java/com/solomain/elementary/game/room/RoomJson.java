package com.solomain.elementary.game.room;

import tools.jackson.databind.json.JsonMapper;

/** JSON комнаты в Redis. Отдельно, чтобы проверить сериализацию тестом без Redis. */
final class RoomJson {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RoomJson() {
    }
}

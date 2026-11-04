package com.solomain.elementary.game.game;

import tools.jackson.databind.json.JsonMapper;

/**
 * JSON партии в Redis. Модель ядра — обычные records без аннотаций Jackson; что она переживает
 * путь «объект → JSON → объект» без потерь, проверяет JsonSerializationTest в game-core.
 */
final class GameJson {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GameJson() {
    }
}

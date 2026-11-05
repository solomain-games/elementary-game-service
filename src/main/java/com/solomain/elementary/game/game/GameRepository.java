package com.solomain.elementary.game.game;

import com.solomain.elementary.core.model.GameState;

import java.util.Optional;
import java.util.Set;

/** Хранилище партий: одна партия на комнату, ключ — код комнаты. В работе — Redis, в тестах — память. */
public interface GameRepository {

    Optional<GameState> find(String roomCode);

    /** Сохраняет состояние партии и продлевает срок его хранения. */
    void save(String roomCode, GameState state);

    void delete(String roomCode);

    /** Коды комнат всех сохранённых партий — чтобы после перезапуска проверить, кто в них в сети. */
    Set<String> codes();
}

package com.solomain.elementary.game.game;

import com.solomain.elementary.core.model.GameState;

import java.util.Optional;

/** Хранилище партий: одна партия на комнату, ключ — код комнаты. В работе — Redis, в тестах — память. */
public interface GameRepository {

    Optional<GameState> find(String roomCode);

    /** Сохраняет состояние партии и продлевает срок его хранения. */
    void save(String roomCode, GameState state);

    void delete(String roomCode);
}

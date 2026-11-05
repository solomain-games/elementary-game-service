package com.solomain.elementary.game.game;

import com.solomain.elementary.core.model.GameState;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Партии в памяти — для тестов без Redis. Считает сохранения, чтобы проверять, что отказ ничего не пишет. */
public class InMemoryGameRepository implements GameRepository {

    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private int saves;

    @Override
    public Optional<GameState> find(String roomCode) {
        return Optional.ofNullable(games.get(roomCode));
    }

    @Override
    public synchronized void save(String roomCode, GameState state) {
        games.put(roomCode, state);
        saves++;
    }

    @Override
    public void delete(String roomCode) {
        games.remove(roomCode);
    }

    @Override
    public Set<String> codes() {
        return Set.copyOf(games.keySet());
    }

    public synchronized int saves() {
        return saves;
    }
}

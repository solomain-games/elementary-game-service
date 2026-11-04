package com.solomain.elementary.game.game;

import com.solomain.elementary.core.engine.Command;
import com.solomain.elementary.core.engine.CommandResult;
import com.solomain.elementary.core.engine.GameEngine;
import com.solomain.elementary.core.engine.PlayerInfo;
import com.solomain.elementary.core.model.CaseDefinition;
import com.solomain.elementary.core.model.GameSettings;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.room.RoomLocks;
import com.solomain.elementary.game.web.ApiException;
import com.solomain.elementary.game.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Партии: создание при запуске игры и выполнение команд ядра.
 *
 * <p>Команда — это «загрузить состояние → {@link GameEngine#handle} → сохранить», под замком
 * комнаты ({@link RoomLocks}, тот же, что у зала ожидания). Поэтому команды одной партии
 * выполняются строго по очереди: два одновременных хода не могут оба увидеть старое состояние
 * и затереть друг друга.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository games;
    private final CaseCatalog cases;
    private final RoomLocks locks;
    private final RandomGenerator random;
    private final Clock clock;

    public GameService(GameRepository games, CaseCatalog cases, RoomLocks locks, RandomGenerator random,
                       Clock clock) {
        this.games = games;
        this.cases = cases;
        this.locks = locks;
        this.random = random;
        this.clock = clock;
    }

    /**
     * Раздача: новая партия в фазе {@code PROLOGUE} (ТЗ 4.1). Вызывается из
     * {@code RoomService.start} под замком комнаты.
     *
     * @param players            участники в порядке входа в комнату; порядок ходов ядро выберет случайно
     * @param turnTimeoutSeconds таймер хода; {@code null} — без таймера
     */
    public GameState create(String roomCode, CaseDefinition definition, List<PlayerInfo> players, String hostId,
                            Integer turnTimeoutSeconds) {
        Duration turnTimeout = turnTimeoutSeconds == null ? null : Duration.ofSeconds(turnTimeoutSeconds);
        GameState state = GameEngine.start(definition, players, new GameSettings(hostId, turnTimeout), random);
        games.save(roomCode, state);
        log.info("Game {} started: case {}, {} players", roomCode, definition.id(), players.size());
        return state;
    }

    public Optional<GameState> find(String roomCode) {
        return games.find(roomCode);
    }

    /** Комната удалена — партия больше не нужна. */
    public void delete(String roomCode) {
        games.delete(roomCode);
    }

    /**
     * Выполняет команду партии. Принятая команда сохраняет новое состояние; отклонённая
     * ничего не меняет — нарушение правил возвращается значением {@link CommandResult.Rejected}.
     * Замок повторно входимый: можно вызывать и из кода, который уже держит замок комнаты.
     *
     * @throws ApiException {@code GAME_NOT_STARTED}, если в комнате нет партии
     */
    public GameOutcome execute(String roomCode, Command command) {
        return locks.withLock(roomCode, () -> {
            GameState state = games.find(roomCode).orElseThrow(() ->
                    new ApiException(ErrorCode.GAME_NOT_STARTED, "No game in room " + roomCode));
            CaseDefinition definition = cases.find(state.caseId())
                    .orElseThrow(() -> new IllegalStateException("Case " + state.caseId() + " is not loaded"))
                    .definition();
            CommandResult result = GameEngine.handle(definition, state, command, clock.instant(), random);
            if (result instanceof CommandResult.Accepted(GameState newState)) {
                games.save(roomCode, newState);
            }
            return new GameOutcome(state, result);
        });
    }
}

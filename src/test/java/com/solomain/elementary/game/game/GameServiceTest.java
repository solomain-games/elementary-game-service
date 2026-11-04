package com.solomain.elementary.game.game;

import com.solomain.elementary.core.engine.BeginPlay;
import com.solomain.elementary.core.engine.CommandResult;
import com.solomain.elementary.core.engine.PlayCard;
import com.solomain.elementary.core.engine.PlayerInfo;
import com.solomain.elementary.core.error.RuleViolation;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.core.model.Phase;
import com.solomain.elementary.core.model.Player;
import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.cases.CasesProperties;
import com.solomain.elementary.game.room.RoomLocks;
import com.solomain.elementary.game.web.ApiException;
import com.solomain.elementary.game.web.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Партии")
class GameServiceTest {

    private static final CaseCatalog CASES = new CaseCatalog(new CasesProperties(testCasesDir()));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private static final String CODE = "K7Q2PX";
    private static final List<PlayerInfo> PLAYERS =
            List.of(new PlayerInfo("g-anna", "Анна"), new PlayerInfo("g-boris", "Борис"));

    private final InMemoryGameRepository repository = new InMemoryGameRepository();
    private final GameService service = new GameService(repository, CASES, new RoomLocks(), new Random(42), CLOCK);

    private GameState start() {
        return service.create(CODE, CASES.find("demo").orElseThrow().definition(), PLAYERS, "g-anna", null);
    }

    @Test
    @DisplayName("создание: раздача сохранена, партия в предыстории")
    void create() {
        GameState created = start();

        assertThat(repository.find(CODE)).contains(created);
        assertThat(created.phase()).isEqualTo(Phase.PROLOGUE);
        assertThat(created.table()).containsExactly(1);   // карта №1 сразу на столе
    }

    @Test
    @DisplayName("принятая команда сохраняет новое состояние")
    void accepted() {
        start();

        CommandResult result = service.execute(CODE, new BeginPlay()).result();

        assertThat(result).isInstanceOf(CommandResult.Accepted.class);
        assertThat(repository.find(CODE).orElseThrow().phase()).isEqualTo(Phase.PLAYING);
    }

    @Test
    @DisplayName("отклонённая команда ничего не сохраняет")
    void rejected() {
        start();
        int savesBefore = repository.saves();

        CommandResult result = service.execute(CODE, new PlayCard("g-anna", 2)).result();   // ещё предыстория

        assertThat(result).isEqualTo(new CommandResult.Rejected(RuleViolation.WRONG_PHASE));
        assertThat(repository.saves()).isEqualTo(savesBefore);
    }

    @Test
    @DisplayName("в комнате нет партии — GAME_NOT_STARTED")
    void noGame() {
        assertThatThrownBy(() -> service.execute(CODE, new BeginPlay()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.GAME_NOT_STARTED));
    }

    @Test
    @DisplayName("одновременные ходы выполняются по очереди: принят ровно один")
    void concurrentCommands() throws InterruptedException {
        start();
        service.execute(CODE, new BeginPlay());
        GameState playing = repository.find(CODE).orElseThrow();
        Player current = playing.players().get(playing.currentPlayerIndex());

        // Ходящий игрок «нажимает» все свои карты одновременно. Без очереди каждый поток
        // прочитал бы одно и то же состояние, и на столе оказалось бы несколько карт
        List<CommandResult> results = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int card : current.hand()) {
            threads.add(Thread.ofVirtual().start(() ->
                    results.add(service.execute(CODE, new PlayCard(current.id(), card)).result())));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(results).hasSize(current.hand().size());
        assertThat(results).filteredOn(CommandResult.Accepted.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(CommandResult.Rejected.class::isInstance)
                .allSatisfy(result -> assertThat(result)
                        .isEqualTo(new CommandResult.Rejected(RuleViolation.NOT_YOUR_TURN)));

        GameState after = repository.find(CODE).orElseThrow();
        assertThat(after.table()).hasSize(2);   // карта №1 и одна выложенная
        assertThat(after.turnNumber()).isEqualTo(playing.turnNumber() + 1);
    }

    private static String testCasesDir() {
        try {
            return Path.of(GameServiceTest.class.getResource("/test-cases").toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}

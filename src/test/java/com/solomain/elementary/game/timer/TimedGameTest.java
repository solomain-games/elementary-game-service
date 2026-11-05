package com.solomain.elementary.game.timer;

import com.solomain.elementary.core.engine.BeginPlay;
import com.solomain.elementary.core.engine.StartVote;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.cases.CasesProperties;
import com.solomain.elementary.game.game.GameService;
import com.solomain.elementary.game.game.InMemoryGameRepository;
import com.solomain.elementary.game.realtime.RoomPresence;
import com.solomain.elementary.game.realtime.RoomPresenceListener;
import com.solomain.elementary.game.room.CreateRoomRequest;
import com.solomain.elementary.game.room.InMemoryRoomRepository;
import com.solomain.elementary.game.room.RoomCodeGenerator;
import com.solomain.elementary.game.room.RoomEvent;
import com.solomain.elementary.game.room.RoomListener;
import com.solomain.elementary.game.room.RoomLocks;
import com.solomain.elementary.game.room.RoomService;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Г9: таймеры хода и голосования от постановки до срабатывания — через очередь и планировщик,
 * но без Redis и без ожидания: часы переводятся вперёд вручную.
 */
@DisplayName("Таймеры партии")
class TimedGameTest {

    private static final CaseCatalog CASES = new CaseCatalog(new CasesProperties(testCasesDir()));
    /** Проверка присутствия отодвинута на час, чтобы не мешать: здесь никто не «в сети». */
    private static final TimersProperties TIMERS =
            new TimersProperties(true, Duration.ofMinutes(2), Duration.ofHours(1));

    private static final PlayerIdentity ANNA = new PlayerIdentity("g-anna", "Анна");
    private static final PlayerIdentity BORIS = new PlayerIdentity("g-boris", "Борис");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T12:00:00Z"));
    private final InMemoryGameRepository games = new InMemoryGameRepository();
    private final InMemoryTimerQueue queue = new InMemoryTimerQueue();
    private final List<RoomEvent> events = new CopyOnWriteArrayList<>();
    private final RoomListener listener = (room, roomEvents) -> events.addAll(roomEvents);
    private final RoomLocks locks = new RoomLocks();
    private final RoomService rooms = new RoomService(new InMemoryRoomRepository(), CASES, new RoomCodeGenerator(),
            locks, listener, new GameService(games, CASES, locks, new Random(42), clock), queue, TIMERS, clock);
    private final TimerScheduler scheduler = new TimerScheduler(queue, rooms,
            new RoomPresenceListener(new RoomPresence(), rooms, locks, queue, TIMERS, clock), games, TIMERS, clock);

    /** Партия Анны и Бориса после предыстории; таймер хода — 30 секунд или без него. */
    private String playing(Integer turnTimeoutSeconds) {
        String code = rooms.create(ANNA, new CreateRoomRequest("demo", "ru", 4, turnTimeoutSeconds)).code();
        rooms.join(BORIS, code);
        rooms.start(ANNA, code);
        assertThat(rooms.executeGameCommand(ANNA, code, new BeginPlay(), true)).isEmpty();
        events.clear();
        return code;
    }

    private GameState state(String code) {
        return games.find(code).orElseThrow();
    }

    private GameTimer.TurnEnd turnTimer(String code) {
        GameState state = state(code);
        return new GameTimer.TurnEnd(code, state.players().get(state.currentPlayerIndex()).id(), state.turnNumber());
    }

    @Test
    @DisplayName("ход истёк: таймер выкладывает карту за игрока, ход и таймер переходят к следующему")
    void turnTimeout() {
        String code = playing(30);
        GameTimer.TurnEnd first = turnTimer(code);
        assertThat(queue.scheduled()).containsEntry(first, state(code).turnDeadline());

        clock.advance(Duration.ofSeconds(29));
        scheduler.fireDue();
        assertThat(events).isEmpty();   // срок ещё не наступил

        clock.advance(Duration.ofSeconds(1));
        scheduler.fireDue();

        assertThat(events).extracting(RoomEvent::type).contains("CARD_PLAYED");
        RoomEvent played = events.stream().filter(e -> e.type().equals("CARD_PLAYED")).findFirst().orElseThrow();
        assertThat(played.playerId()).isEqualTo(first.playerId());
        assertThat(played.byTimer()).isTrue();
        assertThat(state(code).table()).contains(played.cardNumber());

        GameTimer.TurnEnd second = turnTimer(code);
        assertThat(second.turnNumber()).isEqualTo(first.turnNumber() + 1);
        assertThat(queue.contains(first)).isFalse();
        assertThat(queue.scheduled()).containsEntry(second, state(code).turnDeadline());
    }

    @Test
    @DisplayName("игрок сходил сам — таймер его хода снят, поставлен таймер следующего")
    void turnTakenInTime() {
        String code = playing(30);
        GameTimer.TurnEnd first = turnTimer(code);
        PlayerIdentity mover = first.playerId().equals(ANNA.id()) ? ANNA : BORIS;
        int card = state(code).players().get(state(code).currentPlayerIndex()).hand().getFirst();

        assertThat(rooms.executeGameCommand(mover, code,
                new com.solomain.elementary.core.engine.PlayCard(mover.id(), card), false)).isEmpty();

        assertThat(queue.contains(first)).isFalse();
        assertThat(queue.contains(turnTimer(code))).isTrue();
    }

    @Test
    @DisplayName("без таймера хода — таймеров хода нет")
    void noTurnTimer() {
        String code = playing(null);

        assertThat(queue.scheduled().keySet()).noneMatch(GameTimer.TurnEnd.class::isInstance);
    }

    @Test
    @DisplayName("срок голосования истёк: не проголосовавшие — «против», голосование закончено")
    void voteTimeout() {
        String code = playing(null);
        assertThat(rooms.executeGameCommand(ANNA, code, new StartVote(ANNA.id()), false)).isEmpty();
        Instant deadline = state(code).vote().deadline();
        assertThat(queue.scheduled()).containsEntry(new GameTimer.VoteEnd(code, deadline), deadline);

        clock.advance(Duration.between(clock.instant(), deadline));
        scheduler.fireDue();

        assertThat(state(code).vote()).isNull();
        assertThat(events).extracting(RoomEvent::type).containsAnyOf("VOTE_PASSED", "VOTE_FAILED");
        assertThat(queue.scheduled().keySet()).noneMatch(GameTimer.VoteEnd.class::isInstance);
    }

    @Test
    @DisplayName("отключившийся не вернулся: таймер ожидания — выход из партии")
    void disconnectTimeout() {
        String code = playing(null);
        assertThat(rooms.markDisconnected(code, BORIS.id())).isTrue();
        queue.schedule(new GameTimer.DisconnectWait(code, BORIS.id()), clock.instant().plus(TIMERS.disconnectTimeout()));

        clock.advance(TIMERS.disconnectTimeout());
        scheduler.fireDue();

        assertThat(events).extracting(RoomEvent::type).contains("PLAYER_GAVE_UP");
    }

    @Test
    @DisplayName("после перезапуска: проверка присутствия для каждой сохранённой партии")
    void afterStartup() {
        String code = playing(null);
        queue.cancel(new GameTimer.PresenceCheck(code));

        scheduler.afterStartup();

        assertThat(queue.scheduled())
                .containsEntry(new GameTimer.PresenceCheck(code), clock.instant().plus(TIMERS.reconnectGrace()));
    }

    @Test
    @DisplayName("проверка присутствия: кого нет в сети, тот отключён и ждёт своего таймера")
    void presenceCheck() {
        String code = playing(null);

        clock.advance(TIMERS.reconnectGrace());
        scheduler.fireDue();   // здесь никто не подключён — оба отключены

        assertThat(events).extracting(RoomEvent::type).containsExactly("PLAYER_DISCONNECTED", "PLAYER_DISCONNECTED");
        assertThat(queue.contains(new GameTimer.DisconnectWait(code, ANNA.id()))).isTrue();
        assertThat(queue.contains(new GameTimer.DisconnectWait(code, BORIS.id()))).isTrue();
    }

    private static String testCasesDir() {
        try {
            return Path.of(TimedGameTest.class.getResource("/test-cases").toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}

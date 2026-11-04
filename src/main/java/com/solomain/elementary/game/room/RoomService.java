package com.solomain.elementary.game.room;

import com.solomain.elementary.core.engine.Command;
import com.solomain.elementary.core.engine.CommandResult;
import com.solomain.elementary.core.engine.PlayerInfo;
import com.solomain.elementary.core.engine.PlayerLeft;
import com.solomain.elementary.core.engine.PlayerReturned;
import com.solomain.elementary.core.error.RuleViolation;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.core.model.Phase;
import com.solomain.elementary.core.model.PlayerStatus;
import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.cases.LoadedCase;
import com.solomain.elementary.game.game.GameEvents;
import com.solomain.elementary.game.game.GameOutcome;
import com.solomain.elementary.game.game.GameService;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.web.ApiException;
import com.solomain.elementary.game.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Зал ожидания (протокол, 4.4–4.6): создать комнату, войти, выйти, исключить, начать.
 * Проверяет права и состояние, а каждое изменение выполняет под замком комнаты ({@link RoomLocks}).
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    /** Сколько раз пробовать новый код, если сгенерированный уже занят. */
    private static final int CODE_ATTEMPTS = 10;

    private final RoomRepository rooms;
    private final CaseCatalog cases;
    private final RoomCodeGenerator codes;
    private final RoomLocks locks;
    private final RoomListener listener;
    private final GameService games;
    private final Clock clock;

    public RoomService(RoomRepository rooms, CaseCatalog cases, RoomCodeGenerator codes, RoomLocks locks,
                       RoomListener listener, GameService games, Clock clock) {
        this.rooms = rooms;
        this.cases = cases;
        this.codes = codes;
        this.locks = locks;
        this.listener = listener;
        this.games = games;
        this.clock = clock;
    }

    /** Создаёт комнату; создатель — хозяин и сразу участник. */
    public Room create(PlayerIdentity host, CreateRoomRequest request) {
        LoadedCase loadedCase = cases.find(request.caseId())
                .orElseThrow(() -> new ApiException(ErrorCode.CASE_NOT_FOUND, "Case " + request.caseId() + " not found"));
        if (!loadedCase.supportsLanguage(request.language())) {
            throw new ApiException(ErrorCode.LANGUAGE_NOT_SUPPORTED,
                    "Case " + loadedCase.id() + " has no '" + request.language() + "' translation");
        }
        var member = new Member(host.id(), host.name(), clock.instant());
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            Room room = Room.create(codes.next(), loadedCase.id(), request.language(), request.maxPlayers(),
                    request.turnTimeoutSeconds(), member);
            if (rooms.createIfAbsent(room)) {
                log.info("Room {} created by {} (case {})", room.code(), host.id(), room.caseId());
                return room;
            }
        }
        throw new IllegalStateException("Could not generate a free room code in " + CODE_ATTEMPTS + " attempts");
    }

    /** Открытые сведения о комнате. */
    public RoomInfo info(String code) {
        Room room = find(code);
        var title = cases.find(room.caseId()).map(c -> c.summary().title()).orElse(null);
        return new RoomInfo(room.code(), room.caseId(), title, room.language(), room.members().size(),
                room.maxPlayers(), room.status());
    }

    /**
     * Вход по коду. Повторный вход участника ничего не меняет — это безопасно.
     * Исключённого хозяином в эту комнату больше не пускаем.
     */
    public void join(PlayerIdentity player, String code) {
        update(code, RoomEvent.playerJoined(player.id(), player.name()), room -> {
            if (room.isMember(player.id())) {
                // Участник, который нажал «Выйти» посреди партии, возвращается в неё (ТЗ 4.5)
                if (room.status() == RoomStatus.IN_GAME && hasLeftGame(room, player)) {
                    applyGameCommand(room, new PlayerReturned(player.id()));
                }
                return room;
            }
            if (room.isKicked(player.id())) {
                throw new ApiException(ErrorCode.KICKED_FROM_ROOM,
                        "Player " + player.id() + " was kicked from room " + room.code());
            }
            if (room.status() != RoomStatus.WAITING) {
                throw new ApiException(ErrorCode.GAME_ALREADY_STARTED, "Room " + room.code() + " is already in game");
            }
            if (room.isFull()) {
                throw new ApiException(ErrorCode.ROOM_FULL, "Room " + room.code() + " is full");
            }
            return room.withMember(new Member(player.id(), player.name(), clock.instant()));
        });
    }

    /**
     * «Выйти». В зале ожидания и после финала — уход из комнаты: ушёл хозяин — хозяином
     * становится следующий по времени входа; ушёл последний — комната удаляется.
     * В идущей партии — выход из партии (ТЗ 4.5): карты игрока уходят в резерв, но он остаётся
     * участником комнаты и может вернуться, снова открыв её.
     */
    public void leave(PlayerIdentity player, String code) {
        update(code, RoomEvent.playerLeftRoom(player.id()), room -> {
            requireMember(room, player);
            if (room.status() == RoomStatus.IN_GAME) {
                applyGameCommand(room, new PlayerLeft(player.id()));   // повторный выход ядро отклонит — не страшно
                return room;
            }
            return room.withoutMember(player.id());
        });
    }

    /**
     * Хозяин исключает игрока из зала ожидания — окончательно: вернуться в эту комнату тот не сможет.
     * Исключить того, кого уже нет, — не ошибка.
     */
    public void kick(PlayerIdentity host, String code, String playerId) {
        update(code, RoomEvent.playerKicked(playerId), room -> {
            requireHost(room, host);
            requireWaiting(room, "players can be kicked only in the waiting room");
            if (host.id().equals(playerId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Host cannot kick themselves, use leave");
            }
            return room.isMember(playerId) ? room.withKicked(playerId) : room;
        });
    }

    /**
     * Хозяин запускает игру при любом числе игроков (ТЗ 4.1): раздача карт (партия в фазе
     * {@code PROLOGUE}) и комната — в {@code IN_GAME}. Всё под одним замком комнаты.
     */
    public void start(PlayerIdentity host, String code) {
        update(code, RoomEvent.gameStarted(), room -> {
            requireHost(room, host);
            requireWaiting(room, "game is already started");
            LoadedCase loadedCase = cases.find(room.caseId()).orElseThrow(() ->
                    new ApiException(ErrorCode.CASE_NOT_FOUND, "Case " + room.caseId() + " is no longer available"));
            List<PlayerInfo> players = room.members().stream()
                    .map(member -> new PlayerInfo(member.id(), member.name()))
                    .toList();
            games.create(room.code(), loadedCase.definition(), players, room.hostId(), room.turnTimeoutSeconds());
            return room.withStatus(RoomStatus.IN_GAME);
        });
    }

    /**
     * Команда партии от игрока (протокол, 5.5): проверки комнаты, ядро, сохранение и рассылка —
     * всё под одним замком комнаты.
     *
     * @param hostOnly команда только для хозяина (например, «Начать» после предыстории)
     * @return нарушение правил, если ядро команду отклонило; пусто — команда принята
     * @throws ApiException {@code ROOM_NOT_FOUND}, {@code NOT_IN_ROOM}, {@code GAME_NOT_STARTED}, {@code NOT_HOST}
     */
    public Optional<RuleViolation> executeGameCommand(PlayerIdentity player, String rawCode, Command command,
                                                      boolean hostOnly) {
        String code = normalize(rawCode);
        return locks.withLock(code, () -> {
            Room room = rooms.find(code).orElseThrow(() -> notFound(rawCode));
            requireMember(room, player);
            if (room.status() != RoomStatus.IN_GAME) {
                throw new ApiException(ErrorCode.GAME_NOT_STARTED, "No running game in room " + room.code());
            }
            if (hostOnly) {
                requireHost(room, player);
            }
            return applyGameCommand(room, command);
        });
    }

    /**
     * Выполняет команду ядра для комнаты, которая уже под замком. Принятая команда: номер изменения
     * комнаты растёт (по нему клиенты отбрасывают устаревшие снимки), после финала комната
     * переходит в {@code FINISHED}, всем рассылаются события и снимки.
     */
    private Optional<RuleViolation> applyGameCommand(Room room, Command command) {
        GameOutcome outcome = games.execute(room.code(), command);
        if (outcome.result() instanceof CommandResult.Rejected(RuleViolation violation)) {
            return Optional.of(violation);
        }
        GameState after = ((CommandResult.Accepted) outcome.result()).state();
        Room changed = after.phase() == Phase.FINISHED ? room.withStatus(RoomStatus.FINISHED) : room;
        Room saved = changed.nextVersion();
        rooms.save(saved);
        listener.roomChanged(saved, GameEvents.of(command, outcome.before(), after));
        return Optional.empty();
    }

    private boolean hasLeftGame(Room room, PlayerIdentity player) {
        return games.find(room.code())
                .map(state -> {
                    int index = state.indexOfPlayer(player.id());
                    return index >= 0 && state.players().get(index).status() == PlayerStatus.LEFT;
                })
                .orElse(false);
    }

    // ---------- общее ----------

    private Room find(String code) {
        return rooms.find(normalize(code)).orElseThrow(() -> notFound(code));
    }

    /**
     * Комната для участника: так игрок получает снимок по команде {@code sync}.
     *
     * @throws ApiException {@code ROOM_NOT_FOUND} или {@code NOT_IN_ROOM}
     */
    public Room roomOfMember(PlayerIdentity player, String code) {
        Room room = find(code);
        requireMember(room, player);
        return room;
    }

    /**
     * Сама комната не менялась, но участникам нужен свежий снимок: например, кто-то подключился
     * или отключился. Под замком — чтобы снимок не обогнал изменение, которое делается прямо сейчас.
     */
    public void refresh(String code) {
        locks.withLock(code, () -> {
            rooms.find(code).ifPresent(room -> listener.roomChanged(room, List.of()));
            return null;
        });
    }

    /**
     * Прочитать → изменить → сохранить → сообщить, под замком комнаты. Если изменение вернуло
     * ту же комнату, сохранять и сообщать нечего; если в комнате никого не осталось — она удаляется.
     */
    private void update(String rawCode, RoomEvent event, UnaryOperator<Room> change) {
        String code = normalize(rawCode);
        locks.withLock(code, () -> {
            Room room = rooms.find(code).orElseThrow(() -> notFound(rawCode));
            Room changed = change.apply(room);
            if (changed == room) {
                return null;
            }
            if (changed.isEmpty()) {
                rooms.delete(code);
                games.delete(code);
                log.info("Room {} deleted: no members left", code);
            } else {
                Room saved = changed.nextVersion();
                rooms.save(saved);
                listener.roomChanged(saved, List.of(event));
            }
            return null;
        });
    }

    /** Код из адреса: регистр не важен, мусор сразу считается несуществующей комнатой. */
    private static String normalize(String code) {
        String upper = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return RoomCodeGenerator.CODE.matcher(upper).matches() ? upper : "";
    }

    private static ApiException notFound(String code) {
        return new ApiException(ErrorCode.ROOM_NOT_FOUND, "Room " + code + " not found");
    }

    private static void requireMember(Room room, PlayerIdentity player) {
        if (!room.isMember(player.id())) {
            throw new ApiException(ErrorCode.NOT_IN_ROOM, "Player " + player.id() + " is not in room " + room.code());
        }
    }

    private static void requireHost(Room room, PlayerIdentity player) {
        if (!room.isHost(player.id())) {
            throw new ApiException(ErrorCode.NOT_HOST, "Only the host of room " + room.code() + " can do this");
        }
    }

    private static void requireWaiting(Room room, String detail) {
        if (room.status() != RoomStatus.WAITING) {
            throw new ApiException(ErrorCode.GAME_ALREADY_STARTED, detail);
        }
    }
}

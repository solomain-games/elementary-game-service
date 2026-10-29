package com.solomain.elementary.game.room;

import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.cases.LoadedCase;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.web.ApiException;
import com.solomain.elementary.game.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
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
    private final Clock clock;

    public RoomService(RoomRepository rooms, CaseCatalog cases, RoomCodeGenerator codes, RoomLocks locks,
                       Clock clock) {
        this.rooms = rooms;
        this.cases = cases;
        this.codes = codes;
        this.locks = locks;
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
        update(code, room -> {
            if (room.isMember(player.id())) {
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
     * Уход из зала ожидания. Ушёл хозяин — хозяином становится следующий по времени входа;
     * ушёл последний — комната удаляется. Выход из идущей партии появится вместе с партиями (Г7).
     */
    public void leave(PlayerIdentity player, String code) {
        update(code, room -> {
            requireMember(room, player);
            requireWaiting(room, "leaving a running game is not supported yet");
            return room.withoutMember(player.id());
        });
    }

    /**
     * Хозяин исключает игрока из зала ожидания — окончательно: вернуться в эту комнату тот не сможет.
     * Исключить того, кого уже нет, — не ошибка.
     */
    public void kick(PlayerIdentity host, String code, String playerId) {
        update(code, room -> {
            requireHost(room, host);
            requireWaiting(room, "players can be kicked only in the waiting room");
            if (host.id().equals(playerId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Host cannot kick themselves, use leave");
            }
            return room.isMember(playerId) ? room.withKicked(playerId) : room;
        });
    }

    /**
     * Хозяин запускает игру при любом числе игроков (ТЗ 4.1). Пока только меняет статус комнаты:
     * раздачу и саму партию добавят Г6 и Г7.
     */
    public void start(PlayerIdentity host, String code) {
        update(code, room -> {
            requireHost(room, host);
            requireWaiting(room, "game is already started");
            return room.withStatus(RoomStatus.IN_GAME);
        });
    }

    // ---------- общее ----------

    private Room find(String code) {
        return rooms.find(normalize(code)).orElseThrow(() -> notFound(code));
    }

    /**
     * Прочитать → изменить → сохранить, под замком комнаты. Если изменение вернуло ту же комнату,
     * сохранять нечего; если в комнате никого не осталось — она удаляется.
     */
    private void update(String rawCode, UnaryOperator<Room> change) {
        String code = normalize(rawCode);
        locks.withLock(code, () -> {
            Room room = rooms.find(code).orElseThrow(() -> notFound(rawCode));
            Room changed = change.apply(room);
            if (changed == room) {
                return null;
            }
            if (changed.isEmpty()) {
                rooms.delete(code);
                log.info("Room {} deleted: no members left", code);
            } else {
                rooms.save(changed.nextVersion());
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

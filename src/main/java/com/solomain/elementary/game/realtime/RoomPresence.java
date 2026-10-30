package com.solomain.elementary.game.realtime;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Кто сейчас подключён к какой комнате. Игрок «в сети» в комнате, если хотя бы одна его
 * STOMP-сессия подписана на события этой комнаты. Несколько вкладок — несколько сессий:
 * игрок отключается, только когда закрыты все (протокол, 5.2).
 *
 * <p>Хранится в памяти процесса: после перезапуска сервиса клиенты переподключатся сами
 * и снова подпишутся.
 */
@Component
public class RoomPresence {

    /** Сессия → кто и к каким комнатам подключён. */
    private final Map<String, Connection> sessions = new HashMap<>();

    private record Connection(String playerId, Set<String> roomCodes) {
    }

    /**
     * Сессия подписалась на события комнаты.
     *
     * @return {@code true}, если игрок только что появился в сети в этой комнате
     */
    public synchronized boolean subscribed(String sessionId, String playerId, String roomCode) {
        boolean wasOnline = isOnline(roomCode, playerId);
        sessions.computeIfAbsent(sessionId, id -> new Connection(playerId, new HashSet<>()))
                .roomCodes().add(roomCode);
        return !wasOnline;
    }

    /**
     * Сессия закрылась.
     *
     * @return комнаты, в которых игрок из-за этого перестал быть в сети
     */
    public synchronized Set<String> disconnected(String sessionId) {
        Connection connection = sessions.remove(sessionId);
        if (connection == null) {
            return Set.of();
        }
        return connection.roomCodes().stream()
                .filter(code -> !isOnline(code, connection.playerId()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public synchronized boolean isOnline(String roomCode, String playerId) {
        return sessions.values().stream()
                .anyMatch(c -> c.playerId().equals(playerId) && c.roomCodes().contains(roomCode));
    }
}

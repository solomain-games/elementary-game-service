package com.solomain.elementary.game.realtime;

import java.util.Optional;

/** Адреса STOMP (протокол, 5.2). */
public final class RoomTopics {

    /** Личные снимки; отправляются через convertAndSendToUser, клиент подписан на /user/queue/state. */
    public static final String STATE_QUEUE = "/queue/state";
    /** Ответы на свои отклонённые команды. */
    public static final String ERRORS_QUEUE = "/queue/errors";

    private static final String ROOM_TOPIC_PREFIX = "/topic/room.";

    private RoomTopics() {
    }

    /** Общие события комнаты: /topic/room.{code}. */
    public static String roomTopic(String code) {
        return ROOM_TOPIC_PREFIX + code;
    }

    /** Код комнаты из адреса /topic/room.{code}; пусто — это не адрес комнаты. */
    public static Optional<String> roomCodeOf(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return Optional.empty();
        }
        String code = destination.substring(ROOM_TOPIC_PREFIX.length());
        return code.isEmpty() ? Optional.empty() : Optional.of(code);
    }
}

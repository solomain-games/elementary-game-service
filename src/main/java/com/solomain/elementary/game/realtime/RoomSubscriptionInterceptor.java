package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.Room;
import com.solomain.elementary.game.room.RoomRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;

/**
 * Кто на что может подписаться (протокол, 5.2). Брокер сам по себе пускает любого на любой
 * адрес — без этой проверки можно было бы читать события чужой комнаты по её коду.
 *
 * <ul>
 *   <li>{@code /user/queue/state}, {@code /user/queue/errors} — свои личные очереди: всегда можно;</li>
 *   <li>{@code /topic/room.{code}} — только участнику этой комнаты;</li>
 *   <li>{@code /app/...} — ответ контроллера на подписку ({@code @SubscribeMapping}), проверяет сам контроллер;</li>
 *   <li>всё остальное — нельзя.</li>
 * </ul>
 */
@Component
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Set<String> PERSONAL_QUEUES = Set.of("/user/queue/state", "/user/queue/errors");

    private final RoomRepository rooms;

    public RoomSubscriptionInterceptor(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new StompRejectedException("BAD_REQUEST", "SUBSCRIBE without destination");
        }
        if (PERSONAL_QUEUES.contains(destination) || destination.startsWith("/app/")) {
            return message;
        }
        String code = RoomTopics.roomCodeOf(destination)
                .orElseThrow(() -> new StompRejectedException("BAD_REQUEST", "Unknown destination " + destination));
        Room room = rooms.find(code)
                .orElseThrow(() -> new StompRejectedException("ROOM_NOT_FOUND", "Room " + code + " not found"));
        Principal user = accessor.getUser();
        if (user == null || !room.isMember(user.getName())) {
            throw new StompRejectedException("NOT_IN_ROOM", "Not a member of room " + code);
        }
        return message;
    }
}

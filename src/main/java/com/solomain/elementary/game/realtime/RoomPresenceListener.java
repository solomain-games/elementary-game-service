package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.RoomService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

/**
 * Следит за подключениями: подписка на события комнаты — игрок в сети, закрытие сессии —
 * не в сети. Когда это меняется, участники получают свежие снимки (точка «в сети» у игрока).
 * Отключение во время партии (статус игрока, двухминутный таймер) — в Г10.
 */
@Component
public class RoomPresenceListener {

    private final RoomPresence presence;
    private final RoomService rooms;

    public RoomPresenceListener(RoomPresence presence, RoomService rooms) {
        this.presence = presence;
        this.rooms = rooms;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        Principal user = event.getUser();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (user == null) {
            return;
        }
        RoomTopics.roomCodeOf(accessor.getDestination()).ifPresent(code -> {
            if (presence.subscribed(accessor.getSessionId(), user.getName(), code)) {
                rooms.refresh(code);
            }
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presence.disconnected(event.getSessionId()).forEach(rooms::refresh);
    }
}

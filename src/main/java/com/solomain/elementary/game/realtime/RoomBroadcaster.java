package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.Member;
import com.solomain.elementary.game.room.Room;
import com.solomain.elementary.game.room.RoomEvent;
import com.solomain.elementary.game.room.RoomListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Рассылка по WebSocket: событие — в общий канал комнаты, затем каждому участнику — его
 * личный снимок. Исключённый игрок ещё получает событие {@code PLAYER_KICKED}
 * (он подписан на канал), но снимков ему больше не приходит — он уже не участник.
 */
@Component
public class RoomBroadcaster implements RoomListener {

    private final SimpMessagingTemplate messaging;
    private final RoomPresence presence;
    private final Clock clock;

    public RoomBroadcaster(SimpMessagingTemplate messaging, RoomPresence presence, Clock clock) {
        this.messaging = messaging;
        this.presence = presence;
        this.clock = clock;
    }

    @Override
    public void roomChanged(Room room, RoomEvent event) {
        if (event != null) {
            messaging.convertAndSend(RoomTopics.roomTopic(room.code()), event);
        }
        for (Member member : room.members()) {
            sendSnapshot(room, member.id());
        }
    }

    /** Личный снимок одному игроку (во все его вкладки). */
    public void sendSnapshot(Room room, String playerId) {
        messaging.convertAndSendToUser(playerId, RoomTopics.STATE_QUEUE, snapshot(room));
    }

    /** Снимок зала ожидания. Партия появится в снимке в Г7 — у каждого игрока своя. */
    Snapshot snapshot(Room room) {
        var members = room.members().stream()
                .map(member -> new Snapshot.MemberView(member.id(), member.name(),
                        presence.isOnline(room.code(), member.id())))
                .toList();
        var roomView = new Snapshot.RoomView(room.code(), room.caseId(), room.language(), room.maxPlayers(),
                room.turnTimeoutSeconds(), room.hostId(), room.status(), members, null);
        return new Snapshot(Snapshot.PROTOCOL_VERSION, room.version(), clock.instant(), roomView, null);
    }
}

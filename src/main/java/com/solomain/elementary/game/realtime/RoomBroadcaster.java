package com.solomain.elementary.game.realtime;

import com.solomain.elementary.core.model.CaseDefinition;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.core.view.PlayerView;
import com.solomain.elementary.core.view.PlayerViews;
import com.solomain.elementary.game.cases.CaseCatalog;
import com.solomain.elementary.game.game.GameService;
import com.solomain.elementary.game.room.Member;
import com.solomain.elementary.game.room.Room;
import com.solomain.elementary.game.room.RoomEvent;
import com.solomain.elementary.game.room.RoomListener;
import com.solomain.elementary.game.room.RoomStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Рассылка по WebSocket: события — в общий канал комнаты, затем каждому участнику — его
 * личный снимок. В снимке партия показана глазами этого игрока ({@link PlayerViews}):
 * своя рука, чужие — только числом карт, правильные ответы — только в финале.
 * Исключённый игрок ещё получает {@code PLAYER_KICKED} (он подписан на канал), но снимков
 * ему больше не приходит — он уже не участник.
 */
@Component
public class RoomBroadcaster implements RoomListener {

    private final SimpMessagingTemplate messaging;
    private final RoomPresence presence;
    private final GameService games;
    private final CaseCatalog cases;
    private final Clock clock;

    public RoomBroadcaster(SimpMessagingTemplate messaging, RoomPresence presence, GameService games,
                           CaseCatalog cases, Clock clock) {
        this.messaging = messaging;
        this.presence = presence;
        this.games = games;
        this.cases = cases;
        this.clock = clock;
    }

    @Override
    public void roomChanged(Room room, List<RoomEvent> events) {
        for (RoomEvent event : events) {
            messaging.convertAndSend(RoomTopics.roomTopic(room.code()), event);
        }
        Game game = loadGame(room);   // один раз на рассылку, а не на каждого участника
        for (Member member : room.members()) {
            send(room, member.id(), game);
        }
    }

    /** Личный снимок одному игроку (во все его вкладки). */
    public void sendSnapshot(Room room, String playerId) {
        send(room, playerId, loadGame(room));
    }

    private void send(Room room, String playerId, Game game) {
        messaging.convertAndSendToUser(playerId, RoomTopics.STATE_QUEUE, snapshot(room, playerId, game));
    }

    /** Партия комнаты вместе с делом; {@code null} в зале ожидания. */
    private Game loadGame(Room room) {
        if (room.status() == RoomStatus.WAITING) {
            return null;
        }
        return games.find(room.code())
                .flatMap(state -> cases.find(state.caseId()).map(c -> new Game(c.definition(), state)))
                .orElse(null);
    }

    Snapshot snapshot(Room room, String playerId, Game game) {
        var members = room.members().stream()
                .map(member -> new Snapshot.MemberView(member.id(), member.name(),
                        presence.isOnline(room.code(), member.id())))
                .toList();
        var roomView = new Snapshot.RoomView(room.code(), room.caseId(), room.language(), room.maxPlayers(),
                room.turnTimeoutSeconds(), room.hostId(), room.status(), members, null);
        return new Snapshot(Snapshot.PROTOCOL_VERSION, room.version(), clock.instant(), roomView,
                game == null ? null : game.viewFor(playerId));
    }

    /** Партия и её дело — всё, что нужно ядру, чтобы построить представление игрока. */
    record Game(CaseDefinition definition, GameState state) {

        /** Представление партии для игрока; {@code null}, если он не участвует в партии. */
        PlayerView viewFor(String playerId) {
            return state.indexOfPlayer(playerId) < 0 ? null : PlayerViews.of(definition, state, playerId);
        }
    }
}

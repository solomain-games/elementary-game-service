package com.solomain.elementary.game.game;

import com.solomain.elementary.core.engine.Command;
import com.solomain.elementary.core.engine.DiscardCard;
import com.solomain.elementary.core.engine.PlayCard;
import com.solomain.elementary.core.engine.PlayerDisconnectTimeout;
import com.solomain.elementary.core.engine.PlayerDisconnected;
import com.solomain.elementary.core.engine.PlayerLeft;
import com.solomain.elementary.core.engine.PlayerReturned;
import com.solomain.elementary.core.engine.StartVote;
import com.solomain.elementary.core.engine.TurnTimeout;
import com.solomain.elementary.core.model.GameState;
import com.solomain.elementary.core.model.Phase;
import com.solomain.elementary.game.room.RoomEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * События для анимаций и уведомлений (протокол, 5.4) по принятой команде: что сделал игрок
 * и к чему это привело (голосование закончилось, ходы кончились, подведены итоги).
 */
public final class GameEvents {

    private GameEvents() {
    }

    public static List<RoomEvent> of(Command command, GameState before, GameState after) {
        List<RoomEvent> events = new ArrayList<>();
        switch (command) {
            case PlayCard c -> events.add(RoomEvent.cardPlayed(c.playerId(), c.cardNumber(), false));
            case DiscardCard c -> events.add(RoomEvent.cardDiscarded(c.playerId()));
            case StartVote c -> events.add(RoomEvent.voteStarted(c.playerId()));
            case PlayerLeft c -> events.add(RoomEvent.playerGaveUp(c.playerId()));
            case TurnTimeout c -> newTableCard(before, after)
                    .ifPresent(card -> events.add(RoomEvent.cardPlayed(c.playerId(), card, true)));
            case PlayerDisconnected c -> events.add(RoomEvent.playerDisconnected(c.playerId()));
            case PlayerDisconnectTimeout c -> events.add(RoomEvent.playerGaveUp(c.playerId()));   // не вернулся — как вышел
            case PlayerReturned c -> events.add(RoomEvent.playerReconnected(c.playerId()));
            default -> {
                // остальные команды видны по изменениям ниже
            }
        }
        if (before.vote() != null && after.vote() == null) {
            events.add(RoomEvent.simple(after.phase() == Phase.ANSWERING ? "VOTE_PASSED" : "VOTE_FAILED"));
        }
        if (before.phase() != Phase.NO_MOVES_LEFT && after.phase() == Phase.NO_MOVES_LEFT) {
            events.add(RoomEvent.simple("NO_MOVES_LEFT"));
        }
        if (before.phase() != Phase.FINISHED && after.phase() == Phase.FINISHED) {
            events.add(RoomEvent.simple("RESULTS_SHOWN"));
        }
        return events;
    }

    /** Карта, которую таймер выложил за игрока: новые карты стола добавляются в конец. */
    private static Optional<Integer> newTableCard(GameState before, GameState after) {
        return after.table().size() > before.table().size() ? Optional.of(after.table().getLast()) : Optional.empty();
    }
}

package com.solomain.elementary.game.realtime;

import com.solomain.elementary.game.room.Room;
import com.solomain.elementary.game.room.RoomService;
import com.solomain.elementary.game.security.PlayerIdentity;
import com.solomain.elementary.game.web.ApiException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * Команды клиента по STOMP (протокол, 5.5): кадр SEND на /app/room/{code}/{команда}.
 * Пока работает {@code sync}; игровые команды подключит Г7, предыстория — Г8.
 *
 * <p>Метод возвращает ошибку или {@code null}. {@code @SendToUser(broadcast = false)} отправляет
 * ошибку только в ту вкладку, из которой пришла команда; {@code null} — ничего не отправляется.
 */
@Controller
public class RoomCommandController {

    /** Команды партии и предыстории: известны, но в зале ожидания недопустимы. */
    private static final Set<String> GAME_COMMANDS = Set.of(
            "ready", "begin", "play", "discard", "startVote", "castVote", "submitAnswers", "forceResults");

    private final RoomService rooms;
    private final RoomBroadcaster broadcaster;

    public RoomCommandController(RoomService rooms, RoomBroadcaster broadcaster) {
        this.rooms = rooms;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/room/{code}/{command}")
    @SendToUser(destinations = RoomTopics.ERRORS_QUEUE, broadcast = false)
    public CommandError handle(@DestinationVariable String code, @DestinationVariable String command,
                               @Payload(required = false) Map<String, Object> body, Principal principal) {
        String requestId = body != null && body.get("requestId") instanceof String id ? id : null;
        PlayerIdentity player = player(principal);
        try {
            Room room = rooms.roomOfMember(player, code);
            if (command.equals("sync")) {
                broadcaster.sendSnapshot(room, player.id());
                return null;
            }
            if (GAME_COMMANDS.contains(command)) {
                // Партий пока нет (Г7): любая игровая команда — «игра не начата»
                return new CommandError(requestId, command, "GAME_NOT_STARTED");
            }
            return new CommandError(requestId, command, "UNKNOWN_COMMAND");
        } catch (ApiException e) {
            return new CommandError(requestId, command, e.code().name());
        }
    }

    /** Тело команды не разобрано (не JSON-объект). Имя команды — из адреса назначения. */
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = RoomTopics.ERRORS_QUEUE, broadcast = false)
    public CommandError handleMalformed(@Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        String command = destination == null ? null : destination.substring(destination.lastIndexOf('/') + 1);
        return new CommandError(null, command, "BAD_REQUEST");
    }

    private static PlayerIdentity player(Principal principal) {
        if (principal instanceof JwtAuthenticationToken token) {
            return PlayerIdentity.from(token.getToken());
        }
        // Сюда не попасть: без успешного CONNECT кадры SEND отклоняет StompAuthInterceptor
        throw new IllegalStateException("STOMP session is not authenticated");
    }
}

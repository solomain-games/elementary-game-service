package com.solomain.elementary.game.realtime;

import com.solomain.elementary.core.engine.BeginPlay;
import com.solomain.elementary.core.engine.CastVote;
import com.solomain.elementary.core.engine.Command;
import com.solomain.elementary.core.engine.DiscardCard;
import com.solomain.elementary.core.engine.ForceResults;
import com.solomain.elementary.core.engine.PlayCard;
import com.solomain.elementary.core.engine.StartVote;
import com.solomain.elementary.core.engine.SubmitAnswers;
import com.solomain.elementary.core.error.RuleViolation;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Команды клиента по STOMP (протокол, 5.5): кадр SEND на /app/room/{code}/{команда}.
 *
 * <p>Игрок — всегда из токена сессии; из тела берутся только параметры хода. Принятая команда
 * меняет партию, и все получают новые снимки ({@code RoomBroadcaster}); отклонённая — ничего
 * не меняет, а ошибка уходит только в ту вкладку, откуда пришла команда:
 * {@code @SendToUser(broadcast = false)}. Метод возвращает ошибку или {@code null} — тогда
 * ничего не отправляется.
 */
@Controller
public class RoomCommandController {

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
        Map<String, Object> fields = body == null ? Map.of() : body;
        String requestId = fields.get("requestId") instanceof String id ? id : null;
        PlayerIdentity player = player(principal);
        try {
            if (command.equals("sync")) {
                Room room = rooms.roomOfMember(player, code);
                broadcaster.sendSnapshot(room, player.id());
                return null;
            }
            Command gameCommand = toGameCommand(command, player.id(), fields);
            if (gameCommand == null) {
                return new CommandError(requestId, command, "UNKNOWN_COMMAND");
            }
            Optional<RuleViolation> violation = rooms.executeGameCommand(player, code, gameCommand,
                    command.equals("begin"));
            return violation.map(v -> new CommandError(requestId, command, v.name())).orElse(null);
        } catch (ApiException e) {
            return new CommandError(requestId, command, e.code().name());
        } catch (BadCommandException e) {
            return new CommandError(requestId, command, "BAD_REQUEST");
        }
    }

    /**
     * Команда ядра по имени и телу; {@code null} — такой команды нет.
     * Предыстория ({@code ready}) появится в Г8; пока «Начать» ({@code begin}) — только хозяин.
     */
    private static Command toGameCommand(String command, String playerId, Map<String, Object> body) {
        return switch (command) {
            case "begin" -> new BeginPlay();
            case "play" -> new PlayCard(playerId, intField(body, "cardNumber"));
            case "discard" -> new DiscardCard(playerId, intField(body, "cardNumber"));
            case "startVote" -> new StartVote(playerId);
            case "castVote" -> new CastVote(playerId, booleanField(body, "inFavor"));
            case "submitAnswers" -> new SubmitAnswers(playerId, answersField(body));
            case "forceResults" -> new ForceResults(playerId);
            default -> null;
        };
    }

    private static int intField(Map<String, Object> body, String name) {
        if (body.get(name) instanceof Number number && number.doubleValue() == number.intValue()) {
            return number.intValue();
        }
        throw new BadCommandException(name);
    }

    private static boolean booleanField(Map<String, Object> body, String name) {
        if (body.get(name) instanceof Boolean value) {
            return value;
        }
        throw new BadCommandException(name);
    }

    /** {@code {"answers": {"culprit": "guest"}}}: и ключи, и значения — строки. */
    private static Map<String, String> answersField(Map<String, Object> body) {
        if (!(body.get("answers") instanceof Map<?, ?> raw)) {
            throw new BadCommandException("answers");
        }
        Map<String, String> answers = new HashMap<>();
        raw.forEach((key, value) -> {
            if (!(key instanceof String question) || !(value instanceof String option)) {
                throw new BadCommandException("answers");
            }
            answers.put(question, option);
        });
        return answers;
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

    /** В теле команды не хватает поля или оно не того типа. */
    private static final class BadCommandException extends RuntimeException {

        BadCommandException(String field) {
            super("Missing or invalid field: " + field);
        }
    }
}

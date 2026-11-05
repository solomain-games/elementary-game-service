package com.solomain.elementary.game.timer;

import java.time.Instant;
import java.util.Objects;

/**
 * Что должно произойти по истечении срока. Таймер хранится в Redis строкой ({@link #key()}),
 * поэтому переживает перезапуск сервиса.
 *
 * <p>Ключ однозначно определяет таймер: повторная постановка того же таймера только переносит
 * его срок, а не создаёт второй. Устаревшие таймеры (ход уже сделан, голосование закончилось)
 * не страшны: ядро узнаёт их по номеру хода или сроку голосования и отклоняет.
 */
public sealed interface GameTimer {

    String roomCode();

    /** Строка для хранения; обратно — {@link #parse(String)}. */
    String key();

    /** Время хода истекло — {@code TurnTimeout}. */
    record TurnEnd(String roomCode, String playerId, int turnNumber) implements GameTimer {
        public TurnEnd {
            Objects.requireNonNull(roomCode, "roomCode");
            Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public String key() {
            return "turn:" + roomCode + ":" + turnNumber + ":" + playerId;
        }
    }

    /** Срок голосования истёк — {@code VoteTimeout}. Срок одновременно и номер голосования. */
    record VoteEnd(String roomCode, Instant deadline) implements GameTimer {
        public VoteEnd {
            Objects.requireNonNull(roomCode, "roomCode");
            Objects.requireNonNull(deadline, "deadline");
        }

        @Override
        public String key() {
            return "vote:" + roomCode + ":" + deadline;   // ISO-8601 с наносекундами: ядро сравнивает срок точно
        }
    }

    /** Отключившийся игрок не вернулся — {@code PlayerDisconnectTimeout}. */
    record DisconnectWait(String roomCode, String playerId) implements GameTimer {
        public DisconnectWait {
            Objects.requireNonNull(roomCode, "roomCode");
            Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public String key() {
            return "disconnect:" + roomCode + ":" + playerId;
        }
    }

    /**
     * Проверить, кто из игроков партии так и не появился в сети, и отметить их отключёнными.
     * Нужна после старта партии и после перезапуска сервиса: тогда сервис не видел, как они уходили.
     */
    record PresenceCheck(String roomCode) implements GameTimer {
        public PresenceCheck {
            Objects.requireNonNull(roomCode, "roomCode");
        }

        @Override
        public String key() {
            return "presence:" + roomCode;
        }
    }

    /**
     * Таймер из строки хранения. Последняя часть берётся целиком, поэтому в ней может быть
     * двоеточие (в сроке голосования оно есть).
     *
     * @throws IllegalArgumentException строка не похожа на таймер
     */
    static GameTimer parse(String key) {
        String[] parts = key.split(":", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Not a timer: " + key);
        }
        String rest = parts[1];
        try {
            return switch (parts[0]) {
                case "turn" -> {
                    String[] p = split(rest, 3, key);
                    yield new TurnEnd(p[0], p[2], Integer.parseInt(p[1]));
                }
                case "vote" -> {
                    String[] p = split(rest, 2, key);
                    yield new VoteEnd(p[0], Instant.parse(p[1]));
                }
                case "disconnect" -> {
                    String[] p = split(rest, 2, key);
                    yield new DisconnectWait(p[0], p[1]);
                }
                case "presence" -> new PresenceCheck(rest);
                default -> throw new IllegalArgumentException("Unknown timer type: " + key);
            };
        } catch (RuntimeException e) {
            throw e instanceof IllegalArgumentException iae ? iae : new IllegalArgumentException("Bad timer: " + key, e);
        }
    }

    private static String[] split(String value, int parts, String key) {
        String[] result = value.split(":", parts);
        if (result.length != parts) {
            throw new IllegalArgumentException("Bad timer: " + key);
        }
        return result;
    }
}

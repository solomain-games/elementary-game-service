package com.solomain.elementary.game.room;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Комната: настройки, хозяин и участники. Неизменяемая, как и модель ядра: каждое действие
 * возвращает новую комнату. Правила зала ожидания — здесь, а проверки прав и ошибки — в {@link RoomService}.
 *
 * @param code               короткий код комнаты, он же часть ссылки-приглашения
 * @param caseId             выбранное дело
 * @param language           язык комнаты: на нём все видят тексты дела
 * @param maxPlayers         лимит участников, 1–8
 * @param turnTimeoutSeconds таймер хода; {@code null} — без таймера
 * @param hostId             хозяин: может исключать игроков и запускать игру
 * @param status             зал ожидания, партия или финал
 * @param members            участники в порядке входа
 * @param kickedIds          исключённые хозяином; вернуться в эту комнату они не могут
 * @param createdAt          когда создана
 * @param version            номер изменения; растёт с каждым сохранением (снимки по WebSocket, Г5)
 */
public record Room(
        String code,
        String caseId,
        String language,
        int maxPlayers,
        Integer turnTimeoutSeconds,
        String hostId,
        RoomStatus status,
        List<Member> members,
        List<String> kickedIds,
        Instant createdAt,
        long version) {

    public Room {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        members = List.copyOf(members);
        // null — комната сохранена в Redis до появления этого поля
        kickedIds = kickedIds == null ? List.of() : List.copyOf(kickedIds);
    }

    /** Новая комната в зале ожидания; создатель — хозяин и первый участник. */
    public static Room create(String code, String caseId, String language, int maxPlayers,
                              Integer turnTimeoutSeconds, Member host) {
        return new Room(code, caseId, language, maxPlayers, turnTimeoutSeconds, host.id(),
                RoomStatus.WAITING, List.of(host), List.of(), host.joinedAt(), 0);
    }

    public boolean isMember(String playerId) {
        return member(playerId).isPresent();
    }

    public Optional<Member> member(String playerId) {
        return members.stream().filter(member -> member.id().equals(playerId)).findFirst();
    }

    public boolean isHost(String playerId) {
        return Objects.equals(hostId, playerId);
    }

    public boolean isKicked(String playerId) {
        return kickedIds.contains(playerId);
    }

    // @JsonIgnore: иначе Jackson принял бы isFull() и isEmpty() за свойства и записал их в JSON
    @JsonIgnore
    public boolean isFull() {
        return members.size() >= maxPlayers;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** Добавляет участника в конец списка. */
    public Room withMember(Member member) {
        var updated = new ArrayList<>(members);
        updated.add(member);
        return new Room(code, caseId, language, maxPlayers, turnTimeoutSeconds, hostId, status, updated,
                kickedIds, createdAt, version);
    }

    /**
     * Убирает участника. Если ушёл хозяин, хозяином становится вошедший раньше всех
     * из оставшихся (протокол, 4.6). Если никого не осталось — хозяина нет, комнату пора удалить.
     */
    public Room withoutMember(String playerId) {
        List<Member> remaining = members.stream().filter(member -> !member.id().equals(playerId)).toList();
        String newHost = !isHost(playerId) ? hostId : remaining.isEmpty() ? null : remaining.getFirst().id();
        return new Room(code, caseId, language, maxPlayers, turnTimeoutSeconds, newHost, status, remaining,
                kickedIds, createdAt, version);
    }

    /** Исключает участника окончательно: убирает из комнаты и запоминает, чтобы не пустить обратно. */
    public Room withKicked(String playerId) {
        Room without = withoutMember(playerId);
        var kicked = new ArrayList<>(kickedIds);
        if (!kicked.contains(playerId)) {
            kicked.add(playerId);
        }
        return new Room(code, caseId, language, maxPlayers, turnTimeoutSeconds, without.hostId(), status,
                without.members(), kicked, createdAt, version);
    }

    public Room withStatus(RoomStatus newStatus) {
        return new Room(code, caseId, language, maxPlayers, turnTimeoutSeconds, hostId, newStatus, members,
                kickedIds, createdAt, version);
    }

    /** Та же комната со следующим номером изменения. */
    public Room nextVersion() {
        return new Room(code, caseId, language, maxPlayers, turnTimeoutSeconds, hostId, status, members,
                kickedIds, createdAt, version + 1);
    }
}

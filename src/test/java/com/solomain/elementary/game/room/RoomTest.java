package com.solomain.elementary.game.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Комната")
class RoomTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static Member member(String id) {
        return new Member(id, "Игрок " + id, NOW);
    }

    private static Room roomOf(String... ids) {
        Room room = Room.create("K7Q2PX", "demo", "ru", 3, null, member(ids[0]));
        for (int i = 1; i < ids.length; i++) {
            room = room.withMember(member(ids[i]));
        }
        return room;
    }

    @Test
    @DisplayName("создатель — хозяин и первый участник, зал ожидания")
    void create() {
        Room room = roomOf("a");

        assertThat(room.hostId()).isEqualTo("a");
        assertThat(room.members()).extracting(Member::id).containsExactly("a");
        assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.version()).isZero();
    }

    @Test
    @DisplayName("участники в порядке входа; комната заполнена на лимите")
    void members() {
        Room room = roomOf("a", "b", "c");

        assertThat(room.members()).extracting(Member::id).containsExactly("a", "b", "c");
        assertThat(room.isFull()).isTrue();
        assertThat(roomOf("a", "b").isFull()).isFalse();
    }

    @Test
    @DisplayName("ушёл не хозяин — хозяин прежний")
    void memberLeaves() {
        Room room = roomOf("a", "b", "c").withoutMember("b");

        assertThat(room.hostId()).isEqualTo("a");
        assertThat(room.members()).extracting(Member::id).containsExactly("a", "c");
    }

    @Test
    @DisplayName("ушёл хозяин — хозяин тот, кто вошёл раньше всех из оставшихся")
    void hostLeaves() {
        Room room = roomOf("a", "b", "c").withoutMember("a");

        assertThat(room.hostId()).isEqualTo("b");
    }

    @Test
    @DisplayName("ушёл последний — комната пуста, хозяина нет")
    void lastLeaves() {
        Room room = roomOf("a").withoutMember("a");

        assertThat(room.isEmpty()).isTrue();
        assertThat(room.hostId()).isNull();
    }

    @Test
    @DisplayName("исключённый убран из комнаты и запомнен; хозяин переходит, как при выходе")
    void kick() {
        Room room = roomOf("a", "b", "c").withKicked("b");

        assertThat(room.members()).extracting(Member::id).containsExactly("a", "c");
        assertThat(room.isKicked("b")).isTrue();
        assertThat(room.isKicked("c")).isFalse();
        assertThat(room.withKicked("b").kickedIds()).containsExactly("b");   // без повторов
    }

    @Test
    @DisplayName("изменения не трогают исходную комнату")
    void immutable() {
        Room room = roomOf("a");

        room.withMember(member("b"));
        room.withStatus(RoomStatus.IN_GAME);

        assertThat(room.members()).hasSize(1);
        assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.nextVersion().version()).isEqualTo(1);
    }
}

package com.solomain.elementary.game.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Комната в JSON для Redis")
class RoomJsonTest {

    @Test
    @DisplayName("комната переживает путь «объект → JSON → объект» без потерь")
    void roundTrip() {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");
        Room room = Room.create("K7Q2PX", "demo", "ru", 6, 60, new Member("g-1", "Анна", now))
                .withMember(new Member("g-2", "Борис", now.plusSeconds(5)))
                .withMember(new Member("g-3", "Вера", now.plusSeconds(9)))
                .withKicked("g-3")
                .withStatus(RoomStatus.IN_GAME)
                .nextVersion();

        String json = RoomJson.MAPPER.writeValueAsString(room);
        Room restored = RoomJson.MAPPER.readValue(json, Room.class);

        assertThat(restored).isEqualTo(room);
    }

    @Test
    @DisplayName("комната без таймера")
    void withoutTimer() {
        Room room = Room.create("K7Q2PX", "demo", "ru", 1, null,
                new Member("g-1", "Анна", Instant.parse("2026-10-15T12:00:00Z")));

        Room restored = RoomJson.MAPPER.readValue(RoomJson.MAPPER.writeValueAsString(room), Room.class);

        assertThat(restored.turnTimeoutSeconds()).isNull();
    }

    @Test
    @DisplayName("комната, сохранённая до появления списка исключённых, читается")
    void withoutKickedIds() {
        String json = RoomJson.MAPPER.writeValueAsString(Room.create("K7Q2PX", "demo", "ru", 1, null,
                new Member("g-1", "Анна", Instant.parse("2026-10-15T12:00:00Z"))));
        String oldJson = json.replace("\"kickedIds\":[],", "");

        Room restored = RoomJson.MAPPER.readValue(oldJson, Room.class);

        assertThat(oldJson).doesNotContain("kickedIds");
        assertThat(restored.kickedIds()).isEmpty();
    }
}

package com.solomain.elementary.game.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Кто в сети")
class RoomPresenceTest {

    private final RoomPresence presence = new RoomPresence();

    @Test
    @DisplayName("подписка на комнату — игрок в сети в этой комнате, но не в других")
    void subscribe() {
        assertThat(presence.subscribed("s1", "anna", "K7Q2PX")).isTrue();

        assertThat(presence.isOnline("K7Q2PX", "anna")).isTrue();
        assertThat(presence.isOnline("ABCDEF", "anna")).isFalse();
        assertThat(presence.isOnline("K7Q2PX", "boris")).isFalse();
    }

    @Test
    @DisplayName("вторая вкладка не меняет присутствие; отключение — только когда закрыты все")
    void severalTabs() {
        presence.subscribed("s1", "anna", "K7Q2PX");

        assertThat(presence.subscribed("s2", "anna", "K7Q2PX")).isFalse();
        assertThat(presence.disconnected("s1")).isEmpty();
        assertThat(presence.isOnline("K7Q2PX", "anna")).isTrue();

        assertThat(presence.disconnected("s2")).containsExactly("K7Q2PX");
        assertThat(presence.isOnline("K7Q2PX", "anna")).isFalse();
    }

    @Test
    @DisplayName("незнакомая сессия — ничего не меняется")
    void unknownSession() {
        assertThat(presence.disconnected("nope")).isEmpty();
    }
}

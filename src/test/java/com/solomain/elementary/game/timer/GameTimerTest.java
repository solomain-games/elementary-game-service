package com.solomain.elementary.game.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Таймеры: строка хранения")
class GameTimerTest {

    @Test
    @DisplayName("каждый вид таймера читается из своей строки тем же значением")
    void roundTrip() {
        List<GameTimer> timers = List.of(
                new GameTimer.TurnEnd("K7Q2PX", "g-anna", 12),
                new GameTimer.VoteEnd("K7Q2PX", Instant.parse("2026-11-04T17:40:00.123456789Z")),
                new GameTimer.DisconnectWait("K7Q2PX", "u-3f2a"),
                new GameTimer.PresenceCheck("K7Q2PX"));

        for (GameTimer timer : timers) {
            assertThat(GameTimer.parse(timer.key())).isEqualTo(timer);
        }
    }

    @Test
    @DisplayName("строка таймера читаема: по ней в Redis видно, что это и для кого")
    void readableKey() {
        assertThat(new GameTimer.TurnEnd("K7Q2PX", "g-anna", 12).key()).isEqualTo("turn:K7Q2PX:12:g-anna");
        assertThat(new GameTimer.DisconnectWait("K7Q2PX", "g-anna").key()).isEqualTo("disconnect:K7Q2PX:g-anna");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "turn", "turn:K7Q2PX", "turn:K7Q2PX:x:g-anna", "vote:K7Q2PX:вчера", "alarm:K7Q2PX"})
    @DisplayName("чужая или испорченная строка — IllegalArgumentException")
    void badKey(String key) {
        assertThatThrownBy(() -> GameTimer.parse(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("срок в Redis округляется вверх до миллисекунды: таймер не сработает раньше срока")
    void scoreRoundsUp() {
        assertThat(RedisTimerQueue.score(Instant.parse("2026-11-04T17:40:00.123000000Z"))).isEqualTo(1793814000123L);
        assertThat(RedisTimerQueue.score(Instant.parse("2026-11-04T17:40:00.123000001Z"))).isEqualTo(1793814000124L);
    }
}

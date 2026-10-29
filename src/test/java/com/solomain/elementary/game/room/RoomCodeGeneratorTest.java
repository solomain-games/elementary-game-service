package com.solomain.elementary.game.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Код комнаты")
class RoomCodeGeneratorTest {

    private final RoomCodeGenerator generator = new RoomCodeGenerator();

    @RepeatedTest(20)
    @DisplayName("6 символов из A–Z и 2–9")
    void format() {
        assertThat(generator.next()).matches("^[A-Z2-9]{6}$");
    }

    @Test
    @DisplayName("без похожих символов 0, O, 1, I")
    void noLookalikes() {
        assertThat(RoomCodeGenerator.ALPHABET).doesNotContain("0", "O", "1", "I");
        assertThat(RoomCodeGenerator.ALPHABET).hasSize(32);
    }

    @Test
    @DisplayName("коды не повторяются")
    void unique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSizeGreaterThan(995);   // повтор возможен, но крайне маловероятен
    }
}

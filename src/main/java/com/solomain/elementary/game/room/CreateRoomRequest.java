package com.solomain.elementary.game.room;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Создание комнаты (протокол, 4.4).
 *
 * @param turnTimeoutSeconds {@code null} — без таймера (по умолчанию, ТЗ 4.3)
 */
public record CreateRoomRequest(
        @NotBlank String caseId,
        @NotBlank @Pattern(regexp = "^[a-z]{2}$") String language,
        @NotNull @Min(1) @Max(8) Integer maxPlayers,
        Integer turnTimeoutSeconds) {

    private static final Set<Integer> TURN_TIMEOUTS = Set.of(30, 60, 120);

    /** Bean Validation вызывает этот метод; ошибка придёт с полем {@code turnTimeoutValid}. */
    @AssertTrue(message = "must be null, 30, 60 or 120")
    public boolean isTurnTimeoutValid() {
        return turnTimeoutSeconds == null || TURN_TIMEOUTS.contains(turnTimeoutSeconds);
    }
}

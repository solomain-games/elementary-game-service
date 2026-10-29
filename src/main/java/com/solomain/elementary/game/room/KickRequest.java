package com.solomain.elementary.game.room;

import jakarta.validation.constraints.NotBlank;

/** Исключить игрока из зала ожидания. */
public record KickRequest(@NotBlank String playerId) {
}

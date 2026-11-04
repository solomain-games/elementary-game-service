package com.solomain.elementary.game.game;

import com.solomain.elementary.core.engine.CommandResult;
import com.solomain.elementary.core.model.GameState;

/**
 * Результат команды партии вместе с состоянием до неё — чтобы понять, что изменилось
 * (например, закончилось ли голосование), и сообщить об этом событиями.
 *
 * @param before состояние до команды
 * @param result принята (с новым состоянием) или отклонена
 */
public record GameOutcome(GameState before, CommandResult result) {
}

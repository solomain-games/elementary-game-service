package com.solomain.elementary.game.realtime;

/**
 * Ответ на свою отклонённую команду (протокол, 5.6); приходит только отправителю в /user/queue/errors.
 *
 * @param requestId из команды, если клиент его прислал, — чтобы понять, какая команда отклонена
 * @param command   имя команды
 * @param code      машинный код ошибки
 */
public record CommandError(String requestId, String command, String code) {
}

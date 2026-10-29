package com.solomain.elementary.game.web;

import java.util.List;

/**
 * Ошибка REST в формате Problem Details (RFC 9457) с машинным кодом (протокол, 4.1).
 *
 * @param type   всегда {@code about:blank}
 * @param title  краткое название HTTP-статуса
 * @param status HTTP-статус
 * @param detail пояснение для разработчика (пользователю показывается текст по {@code code})
 * @param code   машинный код ошибки
 * @param errors ошибки отдельных полей; пусто, если ошибка не про поля
 */
public record ApiError(String type, String title, int status, String detail, String code, List<FieldError> errors) {

    /** Ошибка поля запроса. */
    public record FieldError(String field, String message) {
    }
}

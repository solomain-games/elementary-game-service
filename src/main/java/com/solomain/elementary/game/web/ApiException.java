package com.solomain.elementary.game.web;

/**
 * Ожидаемая ошибка бизнес-логики: комнаты нет, нет мест и т. п. Превращается в ответ
 * Problem Details с кодом {@link ErrorCode} в {@link ApiExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    /**
     * @param code   машинный код; по нему клиент выбирает текст для пользователя
     * @param detail пояснение для разработчика
     */
    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}

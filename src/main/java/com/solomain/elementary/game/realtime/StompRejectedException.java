package com.solomain.elementary.game.realtime;

/**
 * Кадр клиента отклонён по правилам протокола (например, подписка на чужую комнату).
 * Клиент получает кадр {@code ERROR} с {@code message:<код>}, и соединение закрывается
 * ({@link com.solomain.elementary.game.security.StompErrorHandler}).
 */
public class StompRejectedException extends RuntimeException {

    private final String code;

    public StompRejectedException(String code, String detail) {
        super(detail);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

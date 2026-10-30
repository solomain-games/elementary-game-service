package com.solomain.elementary.game.security;

import com.solomain.elementary.game.realtime.StompRejectedException;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Формирует кадр {@code ERROR}, когда обработка кадра клиента упала с исключением.
 * После кадра {@code ERROR} Spring закрывает соединение.
 *
 * <p>Ошибка аутентификации получает машинный код {@code message:UNAUTHORIZED}: по нему клиент
 * понимает, что нужно заново выполнить гостевой вход (протокол, 3.2). Отказ по правилам
 * протокола ({@link StompRejectedException}) — свой код, например {@code NOT_IN_ROOM}.
 * Остальные ошибки обрабатываются стандартно.
 */
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    static final String UNAUTHORIZED = "UNAUTHORIZED";

    @Override
    public @Nullable Message<byte[]> handleClientMessageProcessingError(
            @Nullable Message<byte[]> clientMessage, Throwable exception) {
        if (isAuthenticationFailure(exception)) {
            return error(UNAUTHORIZED);
        }
        StompRejectedException rejected = findCause(exception, StompRejectedException.class);
        if (rejected != null) {
            return error(rejected.code());
        }
        return super.handleClientMessageProcessingError(clientMessage, exception);
    }

    private static Message<byte[]> error(String code) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(code);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        for (Throwable t = exception; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    /** Spring оборачивает исключение из перехватчика, поэтому ищем причину по всей цепочке. */
    private static boolean isAuthenticationFailure(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationException) {
                return true;
            }
        }
        return false;
    }
}

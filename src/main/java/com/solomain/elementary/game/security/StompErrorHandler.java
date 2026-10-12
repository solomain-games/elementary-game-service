package com.solomain.elementary.game.security;

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
 * понимает, что нужно заново выполнить гостевой вход (протокол, 3.2). Остальные ошибки
 * обрабатываются стандартно.
 */
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    static final String UNAUTHORIZED = "UNAUTHORIZED";

    @Override
    public @Nullable Message<byte[]> handleClientMessageProcessingError(
            @Nullable Message<byte[]> clientMessage, Throwable exception) {
        if (isAuthenticationFailure(exception)) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            accessor.setMessage(UNAUTHORIZED);
            accessor.setLeaveMutable(true);
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }
        return super.handleClientMessageProcessingError(clientMessage, exception);
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

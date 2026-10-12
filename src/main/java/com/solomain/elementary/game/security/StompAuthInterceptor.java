package com.solomain.elementary.game.security;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Аутентификация по STOMP (протокол, 3.2). Браузер не может передать заголовок
 * {@code Authorization} в WebSocket-рукопожатии, поэтому токен приходит в кадре {@code CONNECT}.
 *
 * <p>При {@code CONNECT} токен проверяется и игрок запоминается как пользователь сессии:
 * Spring подставляет его во все следующие кадры этой сессии. Игрок определяется только
 * по токену — никакие поля сообщений на это не влияют.
 *
 * <p>Ошибка аутентификации превращается в кадр {@code ERROR} с {@code message:UNAUTHORIZED}
 * и разрыв соединения ({@link StompErrorHandler}).
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter authenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;   // пульс (heart-beat) и служебные сообщения
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP ->
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
            case SEND, SUBSCRIBE -> {
                // Страховка: кадры без успешного CONNECT не обрабатываем
                if (accessor.getUser() == null) {
                    throw new AuthenticationCredentialsNotFoundException("STOMP session is not authenticated");
                }
            }
            default -> {
                // DISCONNECT, UNSUBSCRIBE и прочие кадры не требуют проверки
            }
        }
        return message;
    }

    private Authentication authenticate(@Nullable String authorization) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new AuthenticationCredentialsNotFoundException("Bearer token is missing in CONNECT");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            return authenticationConverter.convert(jwtDecoder.decode(token));
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid token", e);
        }
    }
}

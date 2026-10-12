package com.solomain.elementary.game.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Перехватчик STOMP без сервера: на вход — кадры, собранные вручную, вместо проверки
 * подписи — декодер-заглушка, который принимает только строку {@code good}.
 */
class StompAuthInterceptorTest {

    private final JwtDecoder decoder = token -> {
        if (!token.equals("good")) {
            throw new BadJwtException("bad token");
        }
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("g-1")
                .claim("name", "Анна")
                .claim("role", "GUEST")
                .build();
    };

    private final StompAuthInterceptor interceptor =
            new StompAuthInterceptor(decoder, new JwtConfig().jwtAuthenticationConverter());

    @Test
    @DisplayName("CONNECT с действительным токеном: пользователь сессии — игрок из sub")
    void connectSetsUser() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, "Bearer good", null);

        interceptor.preSend(connect, null);

        Principal user = accessor(connect).getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("g-1");
    }

    @Test
    @DisplayName("слово Bearer — без учёта регистра")
    void bearerIsCaseInsensitive() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, "bearer good", null);

        interceptor.preSend(connect, null);

        assertThat(accessor(connect).getUser()).isNotNull();
    }

    @Test
    @DisplayName("CONNECT без заголовка Authorization отклоняется")
    void connectWithoutHeader() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("CONNECT не с Bearer-токеном отклоняется")
    void connectWithOtherScheme() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Basic Z29vZA==", null), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("CONNECT с недействительным токеном отклоняется")
    void connectWithBadToken() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer evil", null), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("SEND и SUBSCRIBE без пользователя сессии отклоняются")
    void framesWithoutUser() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, null), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, null), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("SEND от вошедшего игрока проходит; заголовок Authorization в нём ни на что не влияет")
    void sendWithUser() {
        var player = new TestingAuthenticationToken("g-1", null);
        Message<byte[]> send = frame(StompCommand.SEND, "Bearer evil", player);

        assertThat(interceptor.preSend(send, null)).isSameAs(send);
        assertThat(accessor(send).getUser()).isSameAs(player);
    }

    private static Message<byte[]> frame(StompCommand command, String authorization, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);   // как у Spring: перехватчик может дописать заголовки
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor accessor(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }
}

package com.solomain.elementary.game.config;

import com.solomain.elementary.game.security.StompAuthInterceptor;
import com.solomain.elementary.game.security.StompErrorHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP поверх WebSocket (протокол, раздел 5). Здесь — только точка подключения, адреса
 * и аутентификация (Г2); подписки и команды комнаты добавятся в Г5.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;
    private final WebSocketProperties properties;

    public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor, WebSocketProperties properties) {
        this.stompAuthInterceptor = stompAuthInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Защита от чужих сайтов: браузер всегда присылает Origin, и рукопожатие со страницы
        // не из списка отклоняется. Шлюз меняет Host на адрес сервиса, поэтому даже свой
        // фронтенд выглядит здесь «чужим» и должен быть в списке явно.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(new StompErrorHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");          // брокер в памяти сервиса
        registry.setApplicationDestinationPrefixes("/app");       // команды клиента → @MessageMapping
        registry.setUserDestinationPrefix("/user");               // личные очереди: /user/queue/...
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}

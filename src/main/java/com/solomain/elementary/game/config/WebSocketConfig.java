package com.solomain.elementary.game.config;

import com.solomain.elementary.game.realtime.RoomSubscriptionInterceptor;
import com.solomain.elementary.game.security.StompAuthInterceptor;
import com.solomain.elementary.game.security.StompErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP поверх WebSocket (протокол, раздел 5): точка подключения, адреса, пульс,
 * проверка токена (Г2) и прав на подписку (Г5). Команды — в {@code realtime.RoomCommandController}.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Пульс в обе стороны, мс (протокол, 5.1): обрыв связи замечается за секунды. */
    private static final long HEARTBEAT_MS = 10_000;

    private final StompAuthInterceptor stompAuthInterceptor;
    private final RoomSubscriptionInterceptor roomSubscriptionInterceptor;
    private final WebSocketProperties properties;
    private TaskScheduler messageBrokerTaskScheduler;

    public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor,
                           RoomSubscriptionInterceptor roomSubscriptionInterceptor,
                           WebSocketProperties properties) {
        this.stompAuthInterceptor = stompAuthInterceptor;
        this.roomSubscriptionInterceptor = roomSubscriptionInterceptor;
        this.properties = properties;
    }

    /**
     * Планировщик для пульса брокера. Его создаёт сама конфигурация WebSocket, причём позже
     * этого класса, поэтому он внедряется лениво ({@code @Lazy}) — так советует документация Spring.
     */
    @Autowired
    public void setMessageBrokerTaskScheduler(@Lazy TaskScheduler messageBrokerTaskScheduler) {
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Защита от чужих сайтов: браузер всегда присылает Origin, и рукопожатие со страницы
        // не из списка отклоняется. Шлюз меняет Host на адрес сервиса, поэтому даже свой
        // фронтенд выглядит здесь «чужим» и должен быть в списке явно.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(new StompErrorHandler());
        // setPreserveReceiveOrder(true) здесь не включаем: в Spring 7.0.9 с ним клиент
        // не получает кадр ERROR (отказ в CONNECT или подписке). Возможная гонка «sync обогнал
        // SUBSCRIBE» закрыта на клиенте: он повторяет sync, пока не придёт первый снимок.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")           // брокер в памяти сервиса
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(messageBrokerTaskScheduler);
        // setPreservePublishOrder(true) тоже не включаем: в Spring 7.0.9 он ломает
        // convertAndSendToUser («Expected mutable SimpMessageHeaderAccessor»). Порядок снимков
        // не важен: клиент отбрасывает снимок со старым номером version.
        registry.setApplicationDestinationPrefixes("/app");       // команды клиента → @MessageMapping
        registry.setUserDestinationPrefix("/user");               // личные очереди: /user/queue/...
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Порядок важен: сначала узнаём, кто это (CONNECT), потом проверяем права на подписку
        registration.interceptors(stompAuthInterceptor, roomSubscriptionInterceptor);
    }
}

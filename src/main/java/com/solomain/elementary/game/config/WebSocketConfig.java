package com.solomain.elementary.game.config;

import com.solomain.elementary.game.realtime.ProtocolJson;
import com.solomain.elementary.game.realtime.RoomSubscriptionInterceptor;
import com.solomain.elementary.game.security.StompAuthInterceptor;
import com.solomain.elementary.game.security.StompErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
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

    /**
     * JSON сообщений — с нашими правилами формата (тексты дел как {@code {"ru": "..."}}).
     *
     * <p>Spring Boot добавляет в этот же список свой Jackson-конвертер (со стандартным JsonMapper),
     * а при отправке выбирается первый подходящий. Поэтому наш ставится в начало списка:
     * {@code add} в конец оставил бы первым конвертер Boot, и mix-in не применился бы.
     * {@code true}: стандартные конвертеры (строки, байты) тоже остаются.
     */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        // Наш конвертер первым, чтобы LocalizedText писался как {"ru": ...}. Строки и байты он не берёт:
        // их оставляем StringMessageConverter/ByteArrayMessageConverter (text/plain, как и раньше).
        messageConverters.addFirst(new ProtocolMessageConverter());
        return true;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Порядок важен: сначала узнаём, кто это (CONNECT), потом проверяем права на подписку
        registration.interceptors(stompAuthInterceptor, roomSubscriptionInterceptor);
    }

    /**
     * JSON протокола через {@link ProtocolJson#MAPPER}. Строки и байты не трогает: Jackson-конвертер
     * проверяет только canSerialize, поэтому без этого он забрал бы и String-ответы.
     */
    private static final class ProtocolMessageConverter extends JacksonJsonMessageConverter {

        ProtocolMessageConverter() {
            super(ProtocolJson.MAPPER);
        }

        @Override
        protected boolean canConvertTo(Object payload, MessageHeaders headers) {
            return !isRaw(payload.getClass()) && super.canConvertTo(payload, headers);
        }

        @Override
        protected boolean canConvertFrom(Message<?> message, Class<?> targetClass) {
            return (targetClass == null || !isRaw(targetClass)) && super.canConvertFrom(message, targetClass);
        }

        private static boolean isRaw(Class<?> clazz) {
            return clazz == String.class || clazz == byte[].class;
        }
    }
}

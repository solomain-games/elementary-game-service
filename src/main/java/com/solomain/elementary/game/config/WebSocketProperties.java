package com.solomain.elementary.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Настройки WebSocket.
 *
 * @param allowedOrigins адреса страниц, с которых браузеру разрешено открыть WebSocket;
 *                       пусто — только тот же адрес, что у сервиса
 */
@ConfigurationProperties(prefix = "elementary.websocket")
public record WebSocketProperties(List<String> allowedOrigins) {

    public WebSocketProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}

package com.solomain.elementary.game.config;

import com.solomain.elementary.game.security.ProblemAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Настройки безопасности HTTP. Сервис — resource server: каждый запрос несёт токен
 * {@code Authorization: Bearer ...}, выпущенный user-service, и проверяется локально
 * открытым ключом (ADR 0003). Бины проверки — в {@code security.JwtConfig}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // WebSocket-рукопожатие пропускаем: браузер не умеет передать в нём заголовок
                        // Authorization. Токен проверяется в кадре STOMP CONNECT (StompAuthInterceptor).
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // Служебная страница ошибок Spring: иначе любая ошибка превращалась бы в 401
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint()))
                // Сервис не хранит сессии: каждый запрос несёт свой токен
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF защищает сессии на cookie; с токенами в заголовке он не нужен
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}

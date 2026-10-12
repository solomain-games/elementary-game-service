package com.solomain.elementary.game.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки проверки токенов.
 *
 * @param jwkSetUri адрес открытого ключа user-service в формате JWKS
 * @param issuer    ожидаемое значение {@code iss}; совпадает с {@code elementary.jwt.issuer} в user-service
 */
@Validated
@ConfigurationProperties(prefix = "elementary.jwt")
public record JwtProperties(@NotBlank String jwkSetUri, @NotBlank String issuer) {
}

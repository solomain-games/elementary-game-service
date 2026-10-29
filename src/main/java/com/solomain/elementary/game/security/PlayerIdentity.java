package com.solomain.elementary.game.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Кто отправил запрос — только из проверенного токена, а не из тела запроса (протокол, 2).
 *
 * @param id   id игрока ({@code sub})
 * @param name имя для показа другим игрокам ({@code name})
 */
public record PlayerIdentity(String id, String name) {

    public static PlayerIdentity from(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        return new PlayerIdentity(jwt.getSubject(), name == null || name.isBlank() ? jwt.getSubject() : name);
    }
}

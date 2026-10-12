package com.solomain.elementary.game.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ответ на запрос без токена или с недействительным токеном: {@code 401} в формате
 * Problem Details с кодом {@code UNAUTHORIZED} (протокол, 4.1).
 * Заголовок {@code WWW-Authenticate: Bearer ...} выставляет стандартный обработчик Spring Security.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY = """
            {"type":"about:blank","title":"Unauthorized","status":401,\
            "detail":"Token is missing or invalid","code":"UNAUTHORIZED","errors":[]}""";

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        bearer.commence(request, response, exception);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write(BODY);
    }
}

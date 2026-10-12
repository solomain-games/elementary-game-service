package com.solomain.elementary.game.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Токены для тестов и заглушка user-service, которая отдаёт открытый ключ в формате JWKS.
 *
 * <p>Ключи генерируются при запуске тестов. Заглушка одна на все тесты, поэтому её адрес
 * одинаков и Spring переиспользует один контекст для разных тестовых классов.
 */
final class TestTokens {

    static final String ISSUER = "elementary-user-service";

    private static final RSAKey KEY = generateKey();
    /** Чужой ключ с тем же kid: подделка, которая выдаёт себя за ключ user-service. */
    private static final RSAKey FOREIGN_KEY = generateKey();

    private static final HttpServer JWKS_SERVER = startJwksServer();

    /** Адрес JWKS заглушки; подставляется в {@code elementary.jwt.jwk-set-uri}. */
    static final String JWKS_URI =
            "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/.well-known/jwks.json";

    private TestTokens() {
    }

    static String valid(String playerId, String name) {
        return sign(KEY, claims(playerId, name, ISSUER, Instant.now()));
    }

    static String expired() {
        return sign(KEY, claims("g-expired", "Анна", ISSUER, Instant.now().minus(Duration.ofDays(2))));
    }

    static String signedByForeignKey() {
        return sign(FOREIGN_KEY, claims("g-forged", "Анна", ISSUER, Instant.now()));
    }

    static String withIssuer(String issuer) {
        return sign(KEY, claims("g-other", "Анна", issuer, Instant.now()));
    }

    /** Токен на сутки, как у гостя (TTL 24h в user-service). */
    private static JwtClaimsSet claims(String playerId, String name, String issuer, Instant issuedAt) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(playerId)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofDays(1)))
                .claim("name", name)
                .claim("role", "GUEST")
                .build();
    }

    private static String sign(RSAKey key, JwtClaimsSet claims) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID("test-key")
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Мини-HTTP-сервер из JDK: на один GET большего не нужно. */
    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

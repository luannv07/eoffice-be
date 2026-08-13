package vn.edu.epu.eoffice_be.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    static final String SECRET = "eoffice-be-jwt-secret-fixed-for-all-environments-2026";

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("eoffice-be-test");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(30));
        jwtService = new JwtService(properties, secretKey);
    }

    @Test
    void generateAccessTokenShouldBeParsableWithExpectedClaims() {
        String token = jwtService.generateAccessToken(42L, "alice", Role.ADMIN);

        Claims claims = jwtService.parseAccessToken(token);
        assertEquals("42", claims.getSubject());
        assertEquals("alice", claims.get("username", String.class));
        assertEquals("ADMIN", claims.get("role", String.class));
        assertEquals("access", claims.get("token_type", String.class));
        assertEquals("eoffice-be-test", claims.getIssuer());
        assertNotNull(claims.getId());
    }

    @Test
    void generateRefreshTokenShouldBeParsableWithRefreshType() {
        String token = jwtService.generateRefreshToken(42L, "alice", Role.ADMIN);

        Claims claims = jwtService.parseRefreshToken(token);
        assertEquals("refresh", claims.get("token_type", String.class));
    }

    @Test
    void accessTokenShouldBeRejectedByRefreshParser() {
        String accessToken = jwtService.generateAccessToken(42L, "alice", Role.ADMIN);

        assertThrows(JwtException.class, () -> jwtService.parseRefreshToken(accessToken));
    }

    @Test
    void tamperedTokenShouldBeRejected() {
        String token = jwtService.generateAccessToken(42L, "alice", Role.ADMIN);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".xxxx" + parts[2].substring(4);

        assertThrows(JwtException.class, () -> jwtService.parseAccessToken(tampered));
    }

    @Test
    void tokenSignedWithDifferentSecretShouldBeRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-that-is-long-enough-12345".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("42")
                .claim("token_type", "access")
                .issuer("eoffice-be-test")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> jwtService.parseAccessToken(token));
    }
}

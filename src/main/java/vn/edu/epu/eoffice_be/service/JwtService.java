package vn.edu.epu.eoffice_be.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.config.JwtProperties;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtService {

    static final String CLAIM_USERNAME = "username";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_PROFILE_COMPLETED = "profileCompleted";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    final JwtProperties properties;
    final SecretKey secretKey;

    public String generateAccessToken(Long userId, String username, Role role, boolean profileCompleted) {
        return buildToken(userId, username, role, TYPE_ACCESS, properties.getAccessTokenTtl(), profileCompleted);
    }

    public String generateRefreshToken(Long userId, String username, Role role) {
        return buildToken(userId, username, role, TYPE_REFRESH, properties.getRefreshTokenTtl(), false);
    }

    private String buildToken(Long userId, String username, Role role, String type, Duration ttl, boolean profileCompleted) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, type);
        if (TYPE_ACCESS.equals(type)) {
            builder.claim(CLAIM_PROFILE_COMPLETED, profileCompleted);
        }
        return builder
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parseToken(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new JwtException("Invalid token type: expected access token");
        }
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new JwtException("Invalid token type: expected refresh token");
        }
        return claims;
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

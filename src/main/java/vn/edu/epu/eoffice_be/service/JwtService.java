package vn.edu.epu.eoffice_be.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import com.nimbusds.jose.JOSEException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.config.JwtProperties;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
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
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    final JwtProperties properties;
    final RSAPrivateKey rsaPrivateKey;
    final RSAPublicKey rsaPublicKey;

    public String generateAccessToken(Long userId, String username, Role role) {
        return buildToken(userId, username, role, TYPE_ACCESS, properties.getAccessTokenTtl());
    }

    public String generateRefreshToken(Long userId, String username, Role role) {
        return buildToken(userId, username, role, TYPE_REFRESH, properties.getRefreshTokenTtl());
    }

    private String buildToken(Long userId, String username, Role role, String type, java.time.Duration ttl) {
        Instant now = Instant.now();
        String content = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, type)
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .compact();

        try {
            RSAKey jwk = new RSAKey.Builder(rsaPublicKey)
                    .privateKey(rsaPrivateKey)
                    .build();
            com.nimbusds.jose.JWSObject jws = new com.nimbusds.jose.JWSObject(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwk.getKeyID()).build(),
                    new com.nimbusds.jose.Payload(content));
            jws.sign(new RSASSASigner(rsaPrivateKey));
            return jws.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
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
        try {
            com.nimbusds.jose.JWSObject jws = com.nimbusds.jose.JWSObject.parse(token);
            if (!jws.verify(new RSASSAVerifier(rsaPublicKey))) {
                throw new JwtException("Invalid JWT signature");
            }
            return Jwts.parser()
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(jws.getPayload().toString())
                    .getPayload();
        } catch (ParseException | JOSEException e) {
            throw new JwtException("Malformed or unverifiable JWT", e);
        }
    }
}
package vn.edu.epu.eoffice_be.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret key is not configured: set eoffice.jwt.secret-key (env JWT_SECRET)");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

package vn.edu.epu.eoffice_be.jwt.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "eoffice.jwt")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtProperties {

    String issuer = "eoffice-be";

    String privateKey;

    String publicKey;

    Duration accessTokenTtl = Duration.ofMinutes(15);

    Duration refreshTokenTtl = Duration.ofDays(30);

    int authRateLimit = 10;

    long authRateWindowSeconds = 300;
}
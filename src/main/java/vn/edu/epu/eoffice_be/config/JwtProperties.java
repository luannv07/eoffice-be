package vn.edu.epu.eoffice_be.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "eoffice.jwt")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtProperties {

    String issuer = "eoffice-be";

    String secretKey;

    Duration accessTokenTtl = Duration.ofMinutes(15);

    Duration refreshTokenTtl = Duration.ofDays(30);

    String cookieName = "refresh_token";

    String cookiePath = "/auth";

    boolean cookieSecure = false;

    String cookieSameSite = "Strict";

    List<String> allowedOrigins = new ArrayList<>();
}

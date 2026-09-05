package vn.edu.epu.eoffice_be.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "eoffice.rate-limit")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RateLimitProperties {

    int endpointLimit = 10;

    long endpointWindowSeconds = 300;
}

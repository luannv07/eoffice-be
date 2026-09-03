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
@ConfigurationProperties(prefix = "eoffice.password-reset")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PasswordResetProperties {

    int tokenTtlMinutes = 15;

    String resetPasswordUrl = "http://localhost:5173/reset-password";
}

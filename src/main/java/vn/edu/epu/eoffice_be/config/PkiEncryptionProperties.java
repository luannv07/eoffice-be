package vn.edu.epu.eoffice_be.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** No toString: configuration contains a secret. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "eoffice.pki.encryption")
@Validated
public class PkiEncryptionProperties {
    @NotBlank(message = "PKI_ENCRYPTION_KEY is required")
    private String key;
    @NotBlank(message = "PKI_ENCRYPTION_KEY_ID is required")
    private String keyId;
}

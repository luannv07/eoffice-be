package vn.edu.epu.eoffice_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** No toString: configuration contains a secret. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "eoffice.pki.encryption")
public class PkiEncryptionProperties {
    private String key;
    private String keyId;
}

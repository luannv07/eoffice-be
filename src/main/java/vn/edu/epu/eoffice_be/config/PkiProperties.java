package vn.edu.epu.eoffice_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** File-backed PKI configuration. Deliberately does not expose secrets through toString. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "eoffice.pki")
public class PkiProperties {
    private String caKeystorePath = "./uploads/keystores/ca/ca.p12";
    private String caCertPath = "./uploads/keystores/ca/ca.crt";
    private String userKeystoresDir = "./uploads/keystores/users";
    private String revokedPath = "./uploads/keystores/ca/revoked.json";
    private String defaultKeystorePassword = "changeit";

    public Path caKeystore() { return normalized(caKeystorePath); }
    public Path caCertificate() { return normalized(caCertPath); }
    public Path userKeystores() { return normalized(userKeystoresDir); }
    public Path revocations() { return normalized(revokedPath); }
    public char[] password() { return defaultKeystorePassword == null ? new char[0] : defaultKeystorePassword.toCharArray(); }

    private Path normalized(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Thiếu cấu hình đường dẫn PKI");
        return Path.of(value).toAbsolutePath().normalize();
    }
}

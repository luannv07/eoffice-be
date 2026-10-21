package vn.edu.epu.eoffice_be.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PkiEncryptionPropertiesTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @Import(PkiEncryptionProperties.class)
    static class BindingConfiguration { }

    @ParameterizedTest
    @ValueSource(strings = {"key", "key-id"})
    void missingPropertyPreventsStartup(String missing) {
        String configured = missing.equals("key") ? "key-id=test-key" : "key=" + KEY;
        runner.withPropertyValues("eoffice.pki.encryption." + configured)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().isInstanceOf(ConfigurationPropertiesBindException.class)
                        .hasRootCauseInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankKeyPreventsStartup(String value) {
        runner.withPropertyValues("eoffice.pki.encryption.key-id=test-key")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("blank-key", Map.of("eoffice.pki.encryption.key", value))))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankKeyIdPreventsStartup(String value) {
        runner.withPropertyValues("eoffice.pki.encryption.key=" + KEY)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("blank-key-id", Map.of("eoffice.pki.encryption.key-id", value))))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Test
    void validConfigurationBindsIncludingHyphenatedKeyId() {
        runner.withPropertyValues("eoffice.pki.encryption.key=" + KEY,
                        "eoffice.pki.encryption.key-id=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PkiEncryptionProperties.class);
                    var properties = context.getBean(PkiEncryptionProperties.class);
                    assertThat(properties.getKey()).isEqualTo(KEY);
                    assertThat(properties.getKeyId()).isEqualTo("test-key");
                });
    }
}

package vn.edu.epu.eoffice_be.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtConfig {

    static final String RSA = "RSA";

    final JwtProperties properties;

    @Bean
    public RSAPrivateKey rsaPrivateKey(KeyPair keyPair) {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    @Bean
    public RSAPublicKey rsaPublicKey(KeyPair keyPair) {
        return (RSAPublicKey) keyPair.getPublic();
    }

    @Bean
    public KeyPair jwtKeyPair() {
        if (hasConfiguredKeys()) {
            return new KeyPair(loadPublicKey(), loadPrivateKey());
        }
        return generateDevKeyPair();
    }

    private boolean hasConfiguredKeys() {
        return properties.getPrivateKey() != null && !properties.getPrivateKey().isBlank()
                && properties.getPublicKey() != null && !properties.getPublicKey().isBlank();
    }

    private RSAPrivateKey loadPrivateKey() {
        return decode(properties.getPrivateKey(), true);
    }

    private RSAPublicKey loadPublicKey() {
        return decode(properties.getPublicKey(), false);
    }

    private <T> T decode(String base64Key, boolean privateKey) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Key);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            if (privateKey) {
                return (T) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
            }
            return (T) keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid JWT " + (privateKey ? "private" : "public") + " key", e);
        }
    }

    private KeyPair generateDevKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }
}
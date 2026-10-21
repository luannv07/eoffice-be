package vn.edu.epu.eoffice_be.service;

import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.config.PkiEncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Encrypts keystore bytes in memory; does not access files or the database. */
@Service
public class PkiEncryptionService {
    private final PkiEncryptionProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PkiEncryptionService(PkiEncryptionProperties properties) {
        this.properties = properties;
    }

    /** Validate before generating a CA; no implicit key generation or fallback. */
    public void validateConfiguration() {
        configuredKeyId();
        configuredKey();
    }

    public EncryptedKeystore encrypt(byte[] keystore) {
        if (keystore == null || keystore.length == 0) {
            throw new IllegalArgumentException("Keystore must not be empty");
        }
        String keyId = configuredKeyId();
        SecretKeySpec key = configuredKey();
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, keyId);
            return new EncryptedKeystore(cipher.doFinal(keystore), nonce, keyId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PKI keystore encryption failed");
        }
    }

    public byte[] decrypt(EncryptedKeystore encrypted) {
        Objects.requireNonNull(encrypted, "Encrypted keystore is required");
        String keyId = configuredKeyId();
        if (!keyId.equals(encrypted.keyId())) {
            throw new IllegalArgumentException("PKI encryption key ID does not match configuration");
        }
        SecretKeySpec key = configuredKey();
        try {
            return cipher(Cipher.DECRYPT_MODE, key, encrypted.nonce(), keyId)
                    .doFinal(encrypted.ciphertext());
        } catch (GeneralSecurityException e) {
            // Never return unauthenticated plaintext or fall back to another key.
            throw new IllegalStateException("PKI keystore decryption failed: wrong key or modified data");
        }
    }

    private Cipher cipher(int mode, SecretKeySpec key, byte[] nonce, String keyId)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("eoffice-pki-keystore:aes256gcm:" + keyId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private String configuredKeyId() {
        String keyId = properties.getKeyId();
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalStateException("PKI_ENCRYPTION_KEY_ID must contain 1-100 letters, digits, dots, underscores or hyphens");
        }
        return keyId;
    }

    private SecretKeySpec configuredKey() {
        String encoded = properties.getKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("PKI_ENCRYPTION_KEY is required");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PKI_ENCRYPTION_KEY must be Base64 encoding of 32 bytes");
        }
        try {
            if (decoded.length != 32) {
                throw new IllegalStateException("PKI_ENCRYPTION_KEY must be Base64 encoding of 32 bytes");
            }
            return new SecretKeySpec(decoded, "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    /** Ciphertext includes the 16-byte GCM authentication tag. No secret-bearing toString. */
    public record EncryptedKeystore(byte[] ciphertext, byte[] nonce, String keyId) {
        public EncryptedKeystore {
            if (ciphertext == null || ciphertext.length <= 16 || nonce == null || nonce.length != 12
                    || keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,100}")) {
                throw new IllegalArgumentException("Invalid encrypted PKI keystore structure");
            }
            ciphertext = ciphertext.clone();
            nonce = nonce.clone();
        }

        @Override public byte[] ciphertext() { return ciphertext.clone(); }
        @Override public byte[] nonce() { return nonce.clone(); }
        @Override public String toString() { return "EncryptedKeystore[redacted]"; }
    }
}

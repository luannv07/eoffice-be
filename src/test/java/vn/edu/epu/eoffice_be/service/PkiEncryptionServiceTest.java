package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.config.PkiEncryptionProperties;
import vn.edu.epu.eoffice_be.service.PkiEncryptionService.EncryptedKeystore;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PkiEncryptionServiceTest {
    private static final byte[] INPUT = "test-keystore-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTripUsesFreshNonceAndLeavesInputUntouched() {
        var service = service((byte) 7, "test-key");
        byte[] input = INPUT.clone();
        var first = service.encrypt(input);
        var second = service.encrypt(input);
        assertArrayEquals(INPUT, service.decrypt(first));
        assertArrayEquals(INPUT, service.decrypt(second));
        assertArrayEquals(INPUT, input);
        assertEquals(12, first.nonce().length);
        assertEquals(INPUT.length + 16, first.ciphertext().length);
        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
        assertEquals("test-key", first.keyId());
    }

    @Test
    void freshServiceWithSameConfigurationCanDecryptAfterRestart() {
        var encrypted = service((byte) 7, "test-key").encrypt(INPUT);
        assertArrayEquals(INPUT, service((byte) 7, "test-key").decrypt(encrypted));
    }

    @Test
    void wrongKeyAndUnknownKeyIdAreRejected() {
        var encrypted = service((byte) 7, "test-key").encrypt(INPUT);
        assertThrows(IllegalStateException.class,
                () -> service((byte) 8, "test-key").decrypt(encrypted));
        assertThrows(IllegalArgumentException.class,
                () -> service((byte) 7, "another-key").decrypt(encrypted));
    }

    @Test
    void ciphertextTagAndNonceTamperingAreRejected() {
        var service = service((byte) 7, "test-key");
        var encrypted = service.encrypt(INPUT);
        for (int position : new int[]{0, encrypted.ciphertext().length - 1}) {
            byte[] changed = encrypted.ciphertext();
            changed[position] ^= 1;
            assertThrows(IllegalStateException.class,
                    () -> service.decrypt(new EncryptedKeystore(changed, encrypted.nonce(), encrypted.keyId())));
        }
        byte[] nonce = encrypted.nonce();
        nonce[0] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> service.decrypt(new EncryptedKeystore(encrypted.ciphertext(), nonce, encrypted.keyId())));
    }

    @Test
    void missingMalformedOrWrongLengthKeyIsRejectedWithoutLeakingValue() {
        for (String invalid : new String[]{null, "", "secret-not-base64!",
                Base64.getEncoder().encodeToString(new byte[16])}) {
            var properties = properties((byte) 7, "test-key");
            properties.setKey(invalid);
            var error = assertThrows(IllegalStateException.class,
                    () -> new PkiEncryptionService(properties).encrypt(INPUT));
            if (invalid != null && !invalid.isEmpty()) assertFalse(error.getMessage().contains(invalid));
            assertNull(error.getCause());
        }
    }

    @Test
    void invalidKeyIdsAndEmptyKeystoresAreRejected() {
        for (String invalid : new String[]{null, "", "invalid key", "x".repeat(101)}) {
            assertThrows(IllegalStateException.class, () -> service((byte) 7, invalid).encrypt(INPUT));
        }
        var service = service((byte) 7, "test-key");
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(null));
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptedKeystore(new byte[16], new byte[12], "test-key"));
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptedKeystore(new byte[17], new byte[11], "test-key"));
    }

    @Test
    void encryptedResultCopiesArraysAndRedactsToString() {
        var service = service((byte) 7, "test-key");
        var encrypted = service.encrypt(INPUT);
        byte[] ciphertext = encrypted.ciphertext();
        byte[] nonce = encrypted.nonce();
        var copy = new EncryptedKeystore(ciphertext, nonce, encrypted.keyId());
        ciphertext[0] ^= 1;
        nonce[0] ^= 1;
        copy.ciphertext()[0] ^= 1;
        copy.nonce()[0] ^= 1;
        assertArrayEquals(INPUT, service.decrypt(copy));
        assertEquals("EncryptedKeystore[redacted]", copy.toString());
    }

    private PkiEncryptionService service(byte value, String keyId) {
        return new PkiEncryptionService(properties(value, keyId));
    }

    private PkiEncryptionProperties properties(byte value, String keyId) {
        var properties = new PkiEncryptionProperties();
        byte[] key = new byte[32];
        Arrays.fill(key, value); // Synthetic test key only; production never generates a fallback key.
        properties.setKey(Base64.getEncoder().encodeToString(key));
        properties.setKeyId(keyId);
        return properties;
    }
}

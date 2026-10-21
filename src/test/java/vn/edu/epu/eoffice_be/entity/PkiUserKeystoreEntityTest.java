package vn.edu.epu.eoffice_be.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PkiUserKeystoreEntityTest {
    @Test
    void settersCopyInputArrays() {
        var entity = new PkiUserKeystoreEntity();
        byte[] ciphertext = {1, 2, 3};
        byte[] nonce = {4, 5, 6};
        entity.setKeystoreEncrypted(ciphertext);
        entity.setEncryptionNonce(nonce);

        ciphertext[0] = 9;
        nonce[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, entity.getKeystoreEncrypted());
        assertArrayEquals(new byte[]{4, 5, 6}, entity.getEncryptionNonce());
    }

    @Test
    void gettersReturnIndependentCopies() {
        var entity = new PkiUserKeystoreEntity();
        entity.setKeystoreEncrypted(new byte[]{1, 2, 3});
        entity.setEncryptionNonce(new byte[]{4, 5, 6});

        byte[] ciphertext = entity.getKeystoreEncrypted();
        byte[] nonce = entity.getEncryptionNonce();
        ciphertext[0] = 9;
        nonce[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, entity.getKeystoreEncrypted());
        assertArrayEquals(new byte[]{4, 5, 6}, entity.getEncryptionNonce());
        assertNotSame(ciphertext, entity.getKeystoreEncrypted());
        assertNotSame(nonce, entity.getEncryptionNonce());
    }

    @Test
    void accessorsPreserveNullValues() {
        var entity = new PkiUserKeystoreEntity();
        assertNull(entity.getKeystoreEncrypted());
        assertNull(entity.getEncryptionNonce());

        entity.setKeystoreEncrypted(new byte[]{1});
        entity.setEncryptionNonce(new byte[]{2});
        entity.setKeystoreEncrypted(null);
        entity.setEncryptionNonce(null);

        assertNull(entity.getKeystoreEncrypted());
        assertNull(entity.getEncryptionNonce());
    }
}

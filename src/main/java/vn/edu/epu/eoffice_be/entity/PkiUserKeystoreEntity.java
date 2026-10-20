package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Encrypted user keystore only; no generated toString or plaintext private-key field. */
@Entity
@Table(name = "pki_user_keystores", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pki_user_keystores_user_ca", columnNames = {"user_id", "ca_id"}),
        @UniqueConstraint(name = "uq_pki_user_keystores_ca_serial", columnNames = {"ca_id", "certificate_serial"})
})
@Getter
@Setter
@NoArgsConstructor
public class PkiUserKeystoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "ca_id", nullable = false)
    private Long caId;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "text")
    private String certificatePem;

    @Column(name = "certificate_serial", nullable = false, length = 128)
    private String certificateSerial;

    @Column(name = "keystore_encrypted", nullable = false, columnDefinition = "bytea")
    private byte[] keystoreEncrypted;

    @Column(name = "encryption_nonce", nullable = false, columnDefinition = "bytea")
    private byte[] encryptionNonce;

    @Column(name = "encryption_key_id", nullable = false, length = 100)
    private String encryptionKeyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

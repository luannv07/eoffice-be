package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Only encrypted keystore bytes belong here. Deliberately has no generated toString. */
@Entity
@Table(name = "pki_ca")
@Getter
@Setter
@NoArgsConstructor
public class PkiCaEntity {
    public enum Status { ACTIVE, RETIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "text")
    private String certificatePem;

    @Column(name = "keystore_encrypted", nullable = false, columnDefinition = "bytea")
    private byte[] keystoreEncrypted;

    @Column(name = "encryption_nonce", nullable = false, columnDefinition = "bytea")
    private byte[] encryptionNonce;

    @Column(name = "encryption_key_id", nullable = false, length = 100)
    private String encryptionKeyId;

    @Column(name = "fingerprint_sha256", nullable = false, length = 64, unique = true)
    private String fingerprintSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

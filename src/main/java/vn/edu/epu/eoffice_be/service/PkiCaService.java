package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse.*;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;
import vn.edu.epu.eoffice_be.repository.PkiCaRepository;
import vn.edu.epu.eoffice_be.security.pki.Crypto;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigInteger;
import java.util.*;

/** Database-backed CA lifecycle. Never replaces or imports existing material implicitly. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PkiCaService {
    private final PkiProperties properties;
    private final PkiSigningHistory signingHistory;
    private final PkiCaRepository repository;
    private final PkiEncryptionService encryption;

    public PkiCaStatusResponse status() {
        try { return inspectCa().status(); }
        catch (DataAccessException | IOException | SecurityException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    @Transactional
    public PkiCaStatusResponse initialize() {
        try {
            Inspection inspection = inspectCa();
            State state = inspection.status().state();
            if (state == State.READY || state == State.EXPIRED) throw new BusinessException(ErrorCode.PKI_CA_ALREADY_EXISTS);
            if (!inspection.status().canInitialize()) throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
            PkiCaEntity created = createCa();
            // Flush surfaces the PostgreSQL single-ACTIVE constraint in this transaction.
            repository.saveAndFlush(created);
            Inspection validated = inspectRecord(created);
            if (validated.status().state() != State.READY) throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
            return validated.status();
        } catch (DataIntegrityViolationException e) {
            // A concurrent initializer cannot overwrite the winning CA; this transaction rolls back.
            throw new BusinessException(ErrorCode.PKI_CA_ALREADY_EXISTS);
        } catch (DataAccessException | IOException | SecurityException | IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    public Crypto.P12Bundle requireValidCa() {
        try {
            Inspection inspection = inspectCa();
            return switch (inspection.status().state()) {
                case NOT_INITIALIZED -> throw new BusinessException(ErrorCode.PKI_CA_NOT_INITIALIZED);
                case EXPIRED -> throw new BusinessException(ErrorCode.PKI_CA_EXPIRED);
                case RECOVERY_REQUIRED -> throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
                case READY -> inspection.bundle();
            };
        } catch (DataAccessException | IOException | SecurityException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    /** Public verification does not need AES configuration or the CA private key. */
    public Optional<X509Certificate> activeCertificate() {
        try {
            Optional<PkiCaEntity> record = repository.findActive();
            if (record.isEmpty()) return Optional.empty();
            X509Certificate certificate = Crypto.decodeCertPem(record.get().getCertificatePem());
            validatePublicCertificate(certificate);
            if (!certificateFingerprint(certificate).equals(record.get().getFingerprintSha256())) throw new IllegalArgumentException();
            return Optional.of(certificate);
        } catch (GeneralSecurityException | IllegalArgumentException | DataAccessException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    private record Inspection(PkiCaStatusResponse status, Crypto.P12Bundle bundle) { }

    private Inspection inspectCa() throws IOException {
        Optional<PkiCaEntity> active = repository.findActive();
        if (active.isPresent()) return inspectRecord(active.get());
        if (repository.count() > 0) return recovery(Reason.NO_ACTIVE_CA, true, null);
        if (repository.countUserKeystores() > 0) return recovery(Reason.CA_RECORD_MISSING_WITH_USER_KEYSTORES, false, null);
        // Migration guards only. Never fall back to reading private keys from files.
        if (hasLegacyMaterial()) return recovery(Reason.LEGACY_PKI_REQUIRES_MIGRATION, false, null);
        if (signingHistory.hasAnySigningHistory()) return recovery(Reason.CA_RECORD_MISSING_WITH_SIGNING_HISTORY, false, true);
        return new Inspection(new PkiCaStatusResponse(State.NOT_INITIALIZED, true, null, null, null, null), null);
    }

    private Inspection inspectRecord(PkiCaEntity record) {
        byte[] plaintext = null;
        char[] password = properties.password();
        try {
            X509Certificate publicCertificate;
            try { publicCertificate = Crypto.decodeCertPem(record.getCertificatePem()); }
            catch (IllegalArgumentException e) { return recovery(Reason.CA_CERTIFICATE_UNREADABLE, true, null); }
            Crypto.P12Bundle bundle;
            try {
                plaintext = encryption.decrypt(new PkiEncryptionService.EncryptedKeystore(
                        record.getKeystoreEncrypted(), record.getEncryptionNonce(), record.getEncryptionKeyId()));
                bundle = Crypto.decodeP12(plaintext, password);
            } catch (IllegalArgumentException | IllegalStateException e) {
                return recovery(Reason.CA_KEYSTORE_UNREADABLE, true, null);
            }
            X509Certificate root = bundle.getEndEntityCertificate();
            if (bundle.chain().length != 1 || !Arrays.equals(root.getEncoded(), publicCertificate.getEncoded())) {
                return recovery(Reason.CA_CERTIFICATE_MISMATCH, true, null);
            }
            if (!certificateFingerprint(root).equals(record.getFingerprintSha256())) {
                return recovery(Reason.CA_FINGERPRINT_MISMATCH, true, null);
            }
            try { validatePublicCertificate(root); }
            catch (GeneralSecurityException e) { return recovery(Reason.CA_CERTIFICATE_INVALID, true, null); }
            try { verifyKeyPair(bundle); }
            catch (GeneralSecurityException e) { return recovery(Reason.CA_KEY_CERTIFICATE_MISMATCH, true, null); }
            try {
                root.checkValidity();
                return new Inspection(metadata(State.READY, root), bundle);
            } catch (CertificateExpiredException e) {
                return new Inspection(metadata(State.EXPIRED, root), null);
            } catch (CertificateNotYetValidException e) {
                return recovery(Reason.CA_NOT_YET_VALID, true, null);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return recovery(Reason.CA_VALIDATION_FAILED, true, null);
        } finally {
            Arrays.fill(password, '\0');
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private boolean hasLegacyMaterial() throws IOException {
        if (present(properties.caKeystore()) || present(properties.caCertificate()) || present(properties.revocations())) return true;
        for (Path target : List.of(properties.caKeystore(), properties.caCertificate())) {
            Path parent = target.getParent();
            if (parent != null && present(parent)) {
                if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) return true;
                try (var entries = Files.newDirectoryStream(parent)) {
                    for (Path entry : entries) {
                        String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (name.endsWith(".p12") || name.endsWith(".crt") || name.endsWith(".tmp")) return true;
                    }
                }
            }
        }
        Path users = properties.userKeystores();
        if (!present(users)) return false;
        if (!Files.isDirectory(users, LinkOption.NOFOLLOW_LINKS)) return true;
        try (var entries = Files.newDirectoryStream(users)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".p12") || name.endsWith(".crt") || name.endsWith(".tmp")) return true;
            }
        }
        return false;
    }

    private static boolean present(Path path) throws IOException {
        try {
            Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException e) { return false; }
    }

    private PkiCaEntity createCa() {
        char[] password = properties.password();
        byte[] plaintext = null;
        try {
            if (password.length == 0 || Arrays.equals(password, "changeit".toCharArray())) {
                throw new IllegalArgumentException("Configure a non-default keystore password");
            }
            encryption.validateConfiguration();
            Crypto.registerBouncyCastle();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(3072, new SecureRandom());
            KeyPair keys = generator.generateKeyPair();
            Instant now = Instant.now();
            X500Name subject = new X500Name("C=VN,O=Truong Dai hoc Dien Luc - EPU,CN=E-Office Demo Root CA");
            BigInteger serial = new BigInteger(159, new SecureRandom()).add(BigInteger.ONE);
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, serial,
                    Date.from(now.minus(5, ChronoUnit.MINUTES)), Date.from(now.plus(3650, ChronoUnit.DAYS)), subject, keys.getPublic());
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(keys.getPublic()));
            builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(keys.getPublic()));
            X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keys.getPrivate())));
            certificate.verify(keys.getPublic());
            certificate.checkValidity();
            verifyKeyPair(new Crypto.P12Bundle(keys.getPrivate(), new X509Certificate[]{certificate}));


            plaintext = Crypto.encodeP12(password, "eoffice-root-ca", keys.getPrivate(), new Certificate[]{certificate});
            var encrypted = encryption.encrypt(plaintext);
            var record = new PkiCaEntity();
            record.setStatus(PkiCaEntity.Status.ACTIVE);
            record.setCertificatePem(Crypto.encodeCertPem(certificate));
            record.setFingerprintSha256(certificateFingerprint(certificate));
            record.setKeystoreEncrypted(encrypted.ciphertext());
            record.setEncryptionNonce(encrypted.nonce());
            record.setEncryptionKeyId(encrypted.keyId());
            return record;
        } catch (GeneralSecurityException | org.bouncycastle.operator.OperatorCreationException | IOException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        } finally {
            Arrays.fill(password, '\0');
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static void validatePublicCertificate(X509Certificate root) throws GeneralSecurityException {
        boolean[] usage = root.getKeyUsage();
        if (!root.getSubjectX500Principal().equals(root.getIssuerX500Principal()) || root.getBasicConstraints() < 0
                || usage == null || usage.length <= 6 || !usage[5] || !usage[6]) throw new GeneralSecurityException();
        root.verify(root.getPublicKey());
    }

    public static void verifyKeyPair(Crypto.P12Bundle bundle) throws GeneralSecurityException {
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(bundle.privateKey());
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(bundle.getEndEntityCertificate().getPublicKey());
        signature.update(challenge);
        if (!signature.verify(signed)) throw new GeneralSecurityException("Key and certificate do not match");
    }

    public static String certificateFingerprint(X509Certificate certificate) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static Inspection recovery(Reason reason, boolean recordExists, Boolean hasHistory) {
        log.warn("PKI CA recovery required: reason={}, caRecordExists={}", reason, recordExists);
        return new Inspection(new PkiCaStatusResponse(State.RECOVERY_REQUIRED, false, null, null, null, null,
                new Diagnostics(reason, recordExists, hasHistory)), null);
    }

    private static PkiCaStatusResponse metadata(State state, X509Certificate certificate) throws GeneralSecurityException {
        return new PkiCaStatusResponse(state, false, certificate.getSubjectX500Principal().getName(),
                certificateFingerprint(certificate), certificate.getNotBefore().toInstant(), certificate.getNotAfter().toInstant());
    }
}

package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;
import vn.edu.epu.eoffice_be.entity.PkiUserKeystoreEntity;
import vn.edu.epu.eoffice_be.repository.PkiCaRepository;
import vn.edu.epu.eoffice_be.repository.PkiUserKeystoreRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.pki.Crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** User keystores are encrypted and persisted atomically; no private-key files are written. */
@Service
@RequiredArgsConstructor
public class PkiUserKeystoreService {
    private final PkiProperties properties;
    private final PkiCaService caService;
    private final PkiCaRepository caRepository;
    private final PkiUserKeystoreRepository repository;
    private final UserLoginRepository users;
    private final PkiSigningHistory signingHistory;
    private final PkiEncryptionService encryption;

    @Transactional
    public void ensure(String userId, String displayName, String title, String orgUnit) {
        long id = parseUserId(userId);
        try {
            CaContext context = caContext();
            // Lock an existing owner row even when no keystore row exists yet.
            // The lock is held until the calling transaction commits or rolls back.
            users.lockForPkiIssuance(id).orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Người ký không tồn tại hoặc đã bị xóa."));
            var existing = repository.findByUserIdAndCaId(id, context.record().getId());
            if (existing.isPresent()) {
                validate(existing.get(), id, context);
                return;
            }
            if (hasLegacyUserMaterial(userId) || signingHistory.hasUserSigningHistory(userId)) {
                throw invalid("Người dùng còn dữ liệu khóa cũ hoặc lịch sử ký; cần khôi phục trước khi cấp khóa.");
            }
            PkiUserKeystoreEntity created = issue(userId, displayName, title, orgUnit, context);
            validate(created, id, context);
            repository.saveAndFlush(created);
        } catch (DataAccessException | IOException | SecurityException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public Crypto.P12Bundle require(String userId) {
        long id = parseUserId(userId);
        try {
            CaContext context = caContext();
            var record = repository.findByUserIdAndCaId(id, context.record().getId())
                    .orElseThrow(() -> invalid("Không tìm thấy bộ khóa người dùng; cần khôi phục hoặc cấp chứng thư qua luồng ký lần đầu."));
            return validate(record, id, context);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    private record CaContext(PkiCaEntity record, Crypto.P12Bundle bundle) { }

    private CaContext caContext() {
        var bundle = caService.requireValidCa();
        var record = caRepository.findActive().orElseThrow(() -> new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED));
        try {
            if (!PkiCaService.certificateFingerprint(bundle.getEndEntityCertificate()).equals(record.getFingerprintSha256())) {
                throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
            }
        } catch (GeneralSecurityException e) { throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED); }
        return new CaContext(record, bundle);
    }

    private PkiUserKeystoreEntity issue(String userId, String displayName, String title, String orgUnit, CaContext context) {
        char[] password = properties.password();
        byte[] plaintext = null;
        try {
            if (password.length == 0 || Arrays.equals(password, "changeit".toCharArray())) {
                throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
            }
            encryption.validateConfiguration();
            Crypto.P12Bundle caBundle = context.bundle();
            final String SIGNATURE_ALGORITHM = "SHA256withRSA";
            X509Certificate caCert = caBundle.getEndEntityCertificate();
            PrivateKey caKey = caBundle.privateKey();

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(2048, new SecureRandom());
            KeyPair userPair = kpg.generateKeyPair();

            BigInteger serial = new BigInteger(159, new SecureRandom());
            if (serial.compareTo(BigInteger.ZERO) <= 0) {
                serial = serial.abs().add(BigInteger.ONE);
            }

            Instant now = Instant.now();
            Instant notBeforeInstant = now.minus(5, ChronoUnit.MINUTES);
            if (notBeforeInstant.isBefore(caCert.getNotBefore().toInstant())) {
                notBeforeInstant = caCert.getNotBefore().toInstant();
            }
            Date notBefore = Date.from(notBeforeInstant);
            Instant notAfterInstant = now.plus(365 * 3, ChronoUnit.DAYS);
            if (notAfterInstant.isAfter(caCert.getNotAfter().toInstant())) {
                notAfterInstant = caCert.getNotAfter().toInstant();
            }
            Date notAfter = Date.from(notAfterInstant);

            String safeName = (displayName != null && !displayName.isBlank()) ? displayName : "Can bo " + userId;
            X500NameBuilder subBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            subBuilder.addRDN(BCStyle.C, "VN");
            subBuilder.addRDN(BCStyle.O, "Truong Dai hoc Dien Luc - EPU");
            if (orgUnit != null && !orgUnit.isBlank()) {
                subBuilder.addRDN(BCStyle.OU, orgUnit);
            }
            if (title != null && !title.isBlank()) {
                subBuilder.addRDN(BCStyle.T, title);
            }
            subBuilder.addRDN(BCStyle.UID, userId);
            subBuilder.addRDN(BCStyle.CN, safeName);
            X500Name subject = subBuilder.build();
            X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, userPair.getPublic());

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(userPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caCert));

            ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(caKey);
            X509CertificateHolder holder = certBuilder.build(contentSigner);
            X509Certificate userCert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);


            plaintext = Crypto.encodeP12(password, "user", userPair.getPrivate(), new Certificate[]{userCert, caCert});
            var encrypted = encryption.encrypt(plaintext);
            var record = new PkiUserKeystoreEntity();
            record.setUserId(Long.parseLong(userId));
            record.setCaId(context.record().getId());
            record.setCertificatePem(Crypto.encodeCertPem(userCert));
            record.setCertificateSerial(userCert.getSerialNumber().toString(16));
            record.setKeystoreEncrypted(encrypted.ciphertext());
            record.setEncryptionNonce(encrypted.nonce());
            record.setEncryptionKeyId(encrypted.keyId());
            return record;
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        } finally {
            Arrays.fill(password, '\0');
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private Crypto.P12Bundle validate(PkiUserKeystoreEntity record, long userId, CaContext context) {
        char[] password = properties.password();
        byte[] plaintext = null;
        try {
            if (!Objects.equals(record.getUserId(), userId) || !Objects.equals(record.getCaId(), context.record().getId())) {
                throw new GeneralSecurityException();
            }
            plaintext = encryption.decrypt(new PkiEncryptionService.EncryptedKeystore(
                    record.getKeystoreEncrypted(), record.getEncryptionNonce(), record.getEncryptionKeyId()));
            var bundle = Crypto.decodeP12(plaintext, password);
            X509Certificate leaf = bundle.getEndEntityCertificate();
            X509Certificate root = context.bundle().getEndEntityCertificate();
            leaf.checkValidity();
            var uids = X500Name.getInstance(leaf.getSubjectX500Principal().getEncoded()).getRDNs(BCStyle.UID);
            if (uids.length != 1 || uids[0].isMultiValued()
                    || !Long.toString(userId).equals(IETFUtils.valueToString(uids[0].getFirst().getValue()))) throw new GeneralSecurityException();
            boolean[] usage = leaf.getKeyUsage();
            if (leaf.getBasicConstraints() != -1 || usage == null || usage.length < 2 || !usage[0] || !usage[1]) throw new GeneralSecurityException();
            if (bundle.chain().length != 2 || !Arrays.equals(bundle.chain()[1].getEncoded(), root.getEncoded())
                    || !leaf.getIssuerX500Principal().equals(root.getSubjectX500Principal())
                    || leaf.getNotBefore().before(root.getNotBefore()) || leaf.getNotAfter().after(root.getNotAfter())) throw new GeneralSecurityException();
            leaf.verify(root.getPublicKey());
            PkiCaService.verifyKeyPair(bundle);
            if (!Arrays.equals(leaf.getEncoded(), Crypto.decodeCertPem(record.getCertificatePem()).getEncoded())
                    || !leaf.getSerialNumber().toString(16).equals(record.getCertificateSerial())) throw new GeneralSecurityException();
            return bundle;
        } catch (CertificateExpiredException e) {
            throw invalid("Chứng thư người dùng đã hết hạn; không tự tạo lại bộ khóa.");
        } catch (Exception e) {
            throw invalid("Bộ khóa người dùng không hợp lệ; cần kiểm tra hoặc khôi phục bản sao lưu.");
        } finally {
            Arrays.fill(password, '\0');
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Read-only migration guard; never imports, decrypts or locks legacy key files. */
    private boolean hasLegacyUserMaterial(String userId) throws IOException {
        Path directory = properties.userKeystores();
        try { Files.readAttributes(directory, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException e) { return false; }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return true;
        String prefix = "user_" + userId;
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String suffix : List.of(".p12", ".crt")) {
                    String target = prefix + suffix;
                    if (name.equals(target) || name.equals(target + ".tmp")
                            || (name.startsWith("." + target + ".") && name.endsWith(".tmp"))) return true;
                }
            }
        }
        return false;
    }

    private static long parseUserId(String userId) {
        try {
            if (userId == null || !userId.matches("[1-9][0-9]*")) throw new NumberFormatException();
            return Long.parseLong(userId);
        } catch (NumberFormatException e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "Mã người ký không hợp lệ."); }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PKI_USER_KEYSTORE_INVALID, message);
    }
}

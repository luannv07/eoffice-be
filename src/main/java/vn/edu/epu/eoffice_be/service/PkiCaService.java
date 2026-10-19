package vn.edu.epu.eoffice_be.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse.State;
import vn.edu.epu.eoffice_be.security.pki.Crypto;
import vn.edu.epu.eoffice_be.security.pki.PkiFileSupport;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.FileAlreadyExistsException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/** Explicit, file-backed CA lifecycle. Never replaces or silently repairs key material. */
@Service
@RequiredArgsConstructor
public class PkiCaService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final PkiProperties properties;
    private final PkiSigningHistory signingHistory;

    public PkiCaStatusResponse status() {
        try {
            return PkiFileSupport.withJvmLock(lockPath(), this::inspect);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | IllegalArgumentException | SecurityException | DataAccessException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    public PkiCaStatusResponse initialize() {
        try {
            return PkiFileSupport.withLock(lockPath(), () -> {
                CaInspection inspection = inspectCa();
                PkiCaStatusResponse current = inspection.status();
                if (current.state() == State.READY || current.state() == State.EXPIRED) {
                    throw new BusinessException(ErrorCode.PKI_CA_ALREADY_EXISTS);
                }
                if (!current.canInitialize()) throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
                createCa();
                PkiCaStatusResponse created = inspect();
                if (created.state() != State.READY) throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
                return created;
            });
        } catch (BusinessException e) {
            throw e;
        } catch (FileAlreadyExistsException e) {
            throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
        } catch (IOException | IllegalArgumentException | SecurityException | DataAccessException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    /** Called before every keystore-backed signing operation, even for existing users. */
    public Crypto.P12Bundle requireValidCa() {
        try {
            return PkiFileSupport.withLock(lockPath(), () -> {
                CaInspection inspection = inspectCa();
                PkiCaStatusResponse current = inspection.status();
                switch (current.state()) {
                    case NOT_INITIALIZED -> throw new BusinessException(ErrorCode.PKI_CA_NOT_INITIALIZED);
                    case EXPIRED -> throw new BusinessException(ErrorCode.PKI_CA_EXPIRED);
                    case RECOVERY_REQUIRED -> throw new BusinessException(ErrorCode.PKI_CA_RECOVERY_REQUIRED);
                    case READY -> { }
                }
                return inspection.bundle();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | IllegalArgumentException | SecurityException | DataAccessException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    private Path lockPath() {
        Path keystore = properties.caKeystore();
        if (keystore.getFileName() == null) throw new IllegalArgumentException("Đường dẫn keystore CA không hợp lệ");
        return keystore.resolveSibling(keystore.getFileName() + ".lock");
    }

    private record CaInspection(PkiCaStatusResponse status, Crypto.P12Bundle bundle) { }

    private PkiCaStatusResponse inspect() throws IOException {
        return inspectCa().status();
    }

    private CaInspection inspectCa() throws IOException {
        validateLayout();
        boolean hasP12 = present(properties.caKeystore());
        boolean hasCrt = present(properties.caCertificate());
        if (!hasP12 && !hasCrt) {
            if (hasUserArtifacts() || hasCaTemporaryArtifacts() || hasRevocations() || signingHistory.hasAnySigningHistory()) {
                return recoveryInspection();
            }
            return emptyInspection(State.NOT_INITIALIZED);
        }
        if (!hasP12 || !hasCrt) return recoveryInspection();
        try {
            Crypto.P12Bundle bundle = readCa();
            X509Certificate certificate = bundle.getEndEntityCertificate();
            if (!present(properties.revocations())) return recoveryInspection();
            readRevocations();
            try {
                certificate.checkValidity();
                return new CaInspection(metadata(State.READY, certificate), bundle);
            } catch (CertificateExpiredException e) {
                return new CaInspection(metadata(State.EXPIRED, certificate), bundle);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return recoveryInspection();
        } catch (RuntimeException e) {
            if (e instanceof DataAccessException) throw e;
            return recoveryInspection();
        }
    }

    private void validateLayout() throws IOException {
        for (Path path : List.of(properties.caKeystore(), properties.caCertificate(), properties.userKeystores(), properties.revocations())) {
            PkiFileSupport.assertNoSymbolicLinks(path);
        }
        if (properties.caKeystore().equals(properties.caCertificate())
                || properties.caKeystore().equals(properties.revocations())
                || properties.caCertificate().equals(properties.revocations())
                || properties.userKeystores().equals(properties.caKeystore())
                || properties.userKeystores().equals(properties.caCertificate())
                || properties.userKeystores().equals(properties.revocations())) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    private Crypto.P12Bundle readCa() throws IOException, GeneralSecurityException {
        requireReadableRegularFile(properties.caKeystore());
        requireReadableRegularFile(properties.caCertificate());
        char[] password = properties.password();
        try {
            Crypto.P12Bundle bundle;
            X509Certificate publicCertificate;
            try {
                bundle = Crypto.loadP12(properties.caKeystore(), password);
                publicCertificate = Crypto.loadCertPem(properties.caCertificate());
            } catch (RuntimeException e) {
                throw new GeneralSecurityException("Bộ CA không hợp lệ");
            }
            X509Certificate root = bundle.getEndEntityCertificate();
            if (!Arrays.equals(root.getEncoded(), publicCertificate.getEncoded())
                    || !root.getSubjectX500Principal().equals(root.getIssuerX500Principal())
                    || root.getBasicConstraints() < 0
                    || root.getKeyUsage() == null || root.getKeyUsage().length <= 6
                    || !root.getKeyUsage()[5] || !root.getKeyUsage()[6]) {
                throw new GeneralSecurityException("Bộ CA không khớp");
            }
            root.verify(root.getPublicKey());
            verifyKeyPair(bundle);
            return bundle;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void createCa() throws IOException {
        Path p12Temp = null;
        Path crtTemp = null;
        Path revokedTemp = null;
        char[] password = properties.password();
        try {
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

            p12Temp = PkiFileSupport.createTemp(properties.caKeystore());
            crtTemp = PkiFileSupport.createTemp(properties.caCertificate());
            Crypto.saveP12(p12Temp, password, "eoffice-root-ca", keys.getPrivate(), new Certificate[]{certificate});
            Crypto.saveCertPem(crtTemp, certificate);
            if (!present(properties.revocations())) {
                revokedTemp = PkiFileSupport.createTemp(properties.revocations());
                Files.writeString(revokedTemp, "[]");
                PkiFileSupport.publishNew(revokedTemp, properties.revocations());
            }
            PkiFileSupport.ensurePrivateDirectory(properties.userKeystores());
            PkiFileSupport.publishNew(crtTemp, properties.caCertificate());
            // The signing key is published last; readers never consider a partial pair ready.
            PkiFileSupport.publishNew(p12Temp, properties.caKeystore());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR, "Không thể khởi tạo bộ CA; vui lòng kiểm tra cấu hình PKI");
        } finally {
            Arrays.fill(password, '\0');
            deleteTemporary(p12Temp);
            deleteTemporary(crtTemp);
            deleteTemporary(revokedTemp);
        }
    }

    private boolean hasUserArtifacts() throws IOException {
        Path users = properties.userKeystores();
        if (!present(users)) return false;
        if (!Files.isDirectory(users, LinkOption.NOFOLLOW_LINKS)) return true;
        try (var entries = Files.newDirectoryStream(users)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith(".p12") || name.endsWith(".crt") || name.endsWith(".tmp")) return true;
            }
        }
        return false;
    }

    private boolean hasCaTemporaryArtifacts() throws IOException {
        for (Path target : List.of(properties.caKeystore(), properties.caCertificate())) {
            if (present(target.resolveSibling(target.getFileName() + ".tmp"))) return true;
            Path parent = target.getParent();
            if (!present(parent)) continue;
            try (var entries = Files.newDirectoryStream(parent, "." + target.getFileName() + ".*.tmp")) {
                if (entries.iterator().hasNext()) return true;
            }
        }
        if (present(properties.caKeystore().resolveSibling("ca.key.tmp"))) return true;
        return false;
    }

    private boolean hasRevocations() throws IOException {
        if (!present(properties.revocations())) return false;
        try { return !readRevocations().isEmpty(); }
        catch (IllegalArgumentException e) { return true; }
    }

    private List<String> readRevocations() throws IOException {
        byte[] bytes = PkiFileSupport.withJvmLock(properties.revocations(), () -> {
            try (FileChannel channel = FileChannel.open(properties.revocations(), StandardOpenOption.READ);
                 FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
                requireReadableRegularFile(properties.revocations());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    output.write(buffer.array(), 0, buffer.remaining());
                    buffer.clear();
                }
                return output.toByteArray();
            }
        });
        try {
            List<String> serials = JSON.readValue(bytes, new TypeReference<List<String>>() { });
            if (serials == null || serials.stream().anyMatch(value -> value == null || !value.matches("[0-9a-fA-F]+"))) {
                throw new IllegalArgumentException("Danh sách thu hồi không hợp lệ");
            }
            return serials;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Danh sách thu hồi không hợp lệ");
        }
    }

    private static boolean present(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException e) { return false; }
    }

    private static void requireReadableRegularFile(Path path) throws IOException, IllegalArgumentException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.size() == 0) {
            throw new IllegalArgumentException("Tệp PKI không hợp lệ");
        }
        // Probe actual read access separately from cryptographic parsing errors.
        try (var input = Files.newInputStream(path)) { input.read(); }
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
        if (!signature.verify(signed)) throw new GeneralSecurityException("Khóa và chứng thư không khớp");
    }

    public static String certificateFingerprint(X509Certificate certificate) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static PkiCaStatusResponse empty(State state) {
        return new PkiCaStatusResponse(state, state == State.NOT_INITIALIZED, null, null, null, null);
    }

    private static CaInspection emptyInspection(State state) {
        return new CaInspection(empty(state), null);
    }

    private CaInspection recoveryInspection() throws IOException {
        Path certificatePath = properties.caCertificate();
        if (present(certificatePath)) {
            try {
                requireReadableRegularFile(certificatePath);
                return new CaInspection(metadata(State.RECOVERY_REQUIRED, Crypto.loadCertPem(certificatePath)), null);
            } catch (GeneralSecurityException | RuntimeException e) {
                // Metadata is optional and never changes the recovery-required state.
            }
        }
        return emptyInspection(State.RECOVERY_REQUIRED);
    }

    private static PkiCaStatusResponse metadata(State state, X509Certificate certificate) throws GeneralSecurityException {
        return new PkiCaStatusResponse(state, false, certificate.getSubjectX500Principal().getName(),
                certificateFingerprint(certificate), certificate.getNotBefore().toInstant(), certificate.getNotAfter().toInstant());
    }

    private static void deleteTemporary(Path path) {
        if (path != null) {
            try { Files.deleteIfExists(path); }
            catch (IOException ignored) { /* Leftover material intentionally prevents blind reinitialization. */ }
        }
    }
}

package vn.edu.epu.eoffice_be.security.pki;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.config.PkiEncryptionProperties;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;
import vn.edu.epu.eoffice_be.repository.PkiCaRepository;
import vn.edu.epu.eoffice_be.repository.PkiUserKeystoreRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.entity.PkiUserKeystoreEntity;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.service.PkiUserKeystoreService;
import vn.edu.epu.eoffice_be.service.PkiEncryptionService;
import vn.edu.epu.eoffice_be.service.PkiCaService;
import vn.edu.epu.eoffice_be.service.PkiSigningHistory;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.mockito.Mockito.*;

/** PKI fixtures never read keys, trust roots, revocations or images from the working directory. */
public record PkiTestFixture(PkiProperties properties, PkiCaService caService, PkiSigningHistory history,
                             PkiCaRepository repository, PkiEncryptionService encryption,
                             PkiEncryptionProperties encryptionProperties,
                             PkiUserKeystoreRepository userRepository, UserLoginRepository users) {

    public static PkiTestFixture withoutCa(Path directory) {
        PkiProperties properties = new PkiProperties();
        properties.setCaKeystorePath(directory.resolve("ca/ca.p12").toString());
        properties.setCaCertPath(directory.resolve("ca/ca.crt").toString());
        properties.setUserKeystoresDir(directory.resolve("users").toString());
        properties.setRevokedPath(directory.resolve("ca/revoked.json").toString());
        properties.setDefaultKeystorePassword("test-password");
        PkiSigningHistory history = mock(PkiSigningHistory.class);
        PkiCaRepository repository = mock(PkiCaRepository.class);
        var stored = new java.util.concurrent.atomic.AtomicReference<PkiCaEntity>();
        when(repository.findActive()).thenAnswer(invocation -> java.util.Optional.ofNullable(stored.get())
                .filter(ca -> ca.getStatus() == PkiCaEntity.Status.ACTIVE));
        // Optional fixture metadata: signing-only tests do not inspect these counts.
        lenient().when(repository.count()).thenAnswer(invocation -> stored.get() == null ? 0L : 1L);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            PkiCaEntity ca = invocation.getArgument(0);
            synchronized (stored) {
                if (stored.get() != null) throw new org.springframework.dao.DataIntegrityViolationException("Duplicate CA");
                ca.setId(1L);
                stored.set(ca);
            }
            return ca;
        });
        var encryptionProperties = new PkiEncryptionProperties();
        encryptionProperties.setKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        encryptionProperties.setKeyId("synthetic-test-key");
        var encryption = new PkiEncryptionService(encryptionProperties);
        var userRepository = mock(PkiUserKeystoreRepository.class);
        var userRecords = new java.util.concurrent.ConcurrentHashMap<String, PkiUserKeystoreEntity>();
        when(userRepository.findByUserIdAndCaId(anyLong(), anyLong())).thenAnswer(invocation ->
                java.util.Optional.ofNullable(userRecords.get(invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PkiUserKeystoreEntity record = invocation.getArgument(0);
            if (userRecords.putIfAbsent(record.getUserId() + ":" + record.getCaId(), record) != null) {
                throw new org.springframework.dao.DataIntegrityViolationException("Duplicate user keystore");
            }
            record.setId((long) userRecords.size());
            return record;
        });
        lenient().when(userRepository.count()).thenAnswer(invocation -> (long) userRecords.size());
        lenient().when(repository.countUserKeystores()).thenAnswer(invocation -> (long) userRecords.size());
        var users = mock(UserLoginRepository.class);
        when(users.lockForPkiIssuance(anyLong())).thenAnswer(invocation -> {
            var user = new UserLogin();
            user.setId(invocation.getArgument(0));
            return java.util.Optional.of(user);
        });
        return new PkiTestFixture(properties, new PkiCaService(properties, history, repository, encryption),
                history, repository, encryption, encryptionProperties, userRepository, users);
    }

    public static PkiTestFixture create(Path directory) throws Exception {
        Instant now = Instant.now();
        return create(directory, now.minus(1, ChronoUnit.DAYS), now.plus(3650, ChronoUnit.DAYS));
    }

    public static PkiTestFixture create(Path directory, Instant notBefore, Instant notAfter) throws Exception {
        PkiTestFixture fixture = withoutCa(directory);
        Crypto.registerBouncyCastle();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        X500Name subject = new X500Name("C=VN,O=Eoffice Tests,CN=Isolated Test Root CA");
        var builder = new JcaX509v3CertificateBuilder(subject,
                new BigInteger(128, new SecureRandom()).setBit(127), Date.from(notBefore), Date.from(notAfter),
                subject, pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        var extensions = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(pair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(pair.getPublic()));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate());
        var certificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
        Files.createDirectories(fixture.properties.caKeystore().getParent());
        char[] password = fixture.properties.password();
        try {
            byte[] plaintext = Crypto.encodeP12(password, "test-ca", pair.getPrivate(), new Certificate[]{certificate});
            try {
                var encrypted = fixture.encryption.encrypt(plaintext);
                var ca = new PkiCaEntity();
                ca.setStatus(PkiCaEntity.Status.ACTIVE);
                ca.setCertificatePem(Crypto.encodeCertPem(certificate));
                ca.setFingerprintSha256(PkiCaService.certificateFingerprint(certificate));
                ca.setKeystoreEncrypted(encrypted.ciphertext());
                ca.setEncryptionNonce(encrypted.nonce());
                ca.setEncryptionKeyId(encrypted.keyId());
                fixture.repository.saveAndFlush(ca);
            } finally { java.util.Arrays.fill(plaintext, (byte) 0); }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        Files.writeString(fixture.properties.revocations(), "[]");
        return fixture;
    }

    public PdfSigner signer() {
        PdfSigner signer = new PdfSigner(userService());
        Path root = properties.userKeystores().getParent();
        ReflectionTestUtils.setField(signer, "signaturesDir", root.resolve("signatures").toString());
        ReflectionTestUtils.setField(signer, "stampsDir", root.resolve("stamps").toString());
        return signer;
    }

    public PkiUserKeystoreService userService() {
        return new PkiUserKeystoreService(properties, caService, repository, userRepository, users, history, encryption);
    }

    public PdfSignatureVerifier verifier() {
        return new PdfSignatureVerifier(caService, properties);
    }
}

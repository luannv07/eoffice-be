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

import static org.mockito.Mockito.mock;

/** PKI fixtures never read keys, trust roots, revocations or images from the working directory. */
public record PkiTestFixture(PkiProperties properties, PkiCaService caService, PkiSigningHistory history) {

    public static PkiTestFixture withoutCa(Path directory) {
        PkiProperties properties = new PkiProperties();
        properties.setCaKeystorePath(directory.resolve("ca/ca.p12").toString());
        properties.setCaCertPath(directory.resolve("ca/ca.crt").toString());
        properties.setUserKeystoresDir(directory.resolve("users").toString());
        properties.setRevokedPath(directory.resolve("ca/revoked.json").toString());
        properties.setDefaultKeystorePassword("test-password");
        PkiSigningHistory history = mock(PkiSigningHistory.class);
        return new PkiTestFixture(properties, new PkiCaService(properties, history), history);
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
            Crypto.saveP12(fixture.properties.caKeystore(), password, "test-ca", pair.getPrivate(), new Certificate[]{certificate});
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        Crypto.saveCertPem(fixture.properties.caCertificate(), certificate);
        Files.writeString(fixture.properties.revocations(), "[]");
        return fixture;
    }

    public PdfSigner signer() {
        PdfSigner signer = new PdfSigner(properties, caService, history);
        Path root = properties.userKeystores().getParent();
        ReflectionTestUtils.setField(signer, "signaturesDir", root.resolve("signatures").toString());
        ReflectionTestUtils.setField(signer, "stampsDir", root.resolve("stamps").toString());
        return signer;
    }

    public PdfSignatureVerifier verifier() {
        return new PdfSignatureVerifier(properties.caCertificate().toString(), properties.revocations().toString());
    }
}

package vn.edu.epu.eoffice_be.security.pki;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;
import vn.edu.epu.eoffice_be.entity.PkiUserKeystoreEntity;
import vn.edu.epu.eoffice_be.service.PkiCaService;
import vn.edu.epu.eoffice_be.service.PkiEncryptionService;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Synthetic, process-local templates. Mutable records and arrays never escape the cache. */
public final class PkiTestSamples {
    private static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Map<String, KeyPair> KEYS = new ConcurrentHashMap<>();
    private static final Map<String, Material> MATERIALS = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> P12 = new ConcurrentHashMap<>();
    private static final AtomicLong SERIAL = new AtomicLong(100);
    private PkiTestSamples() { }

    private record Material(byte[] key, byte[][] certificates) {
        Crypto.P12Bundle fresh() {
            try {
                var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key.clone()));
                var chain = new X509Certificate[certificates.length];
                for (int i = 0; i < chain.length; i++) {
                    chain[i] = (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509", "BC")
                            .generateCertificate(new java.io.ByteArrayInputStream(certificates[i].clone()));
                }
                return new Crypto.P12Bundle(privateKey, chain);
            } catch (Exception e) { throw new IllegalStateException("Cannot copy synthetic key material", e); }
        }
    }

    public static KeyPair keyPair(String purpose) {
        return KEYS.computeIfAbsent(purpose, ignored -> {
            try {
                Crypto.registerBouncyCastle();
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }

    public static Crypto.P12Bundle root() {
        return root("main", START.minus(1, ChronoUnit.DAYS), START.plus(3650, ChronoUnit.DAYS));
    }

    public static Crypto.P12Bundle root(String purpose, Instant before, Instant after) {
        String identity = "root:" + purpose + ":" + before + ":" + after;
        return MATERIALS.computeIfAbsent(identity, ignored -> {
            long start = System.nanoTime();
            try {
                var pair = keyPair("root:" + purpose);
                var subject = new X500Name("C=VN,O=Eoffice Tests,CN=Isolated Test Root CA " + purpose);
                var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(SERIAL.incrementAndGet()),
                        Date.from(before), Date.from(after), subject, pair.getPublic());
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
                var extensions = new JcaX509ExtensionUtils();
                builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(pair.getPublic()));
                builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(pair.getPublic()));
                var cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(
                        new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate())));
                return new Material(pair.getPrivate().getEncoded().clone(), new byte[][]{cert.getEncoded()});
            } catch (Exception e) { throw new IllegalStateException(e); }
            finally { System.out.printf("PKI_TEMPLATE root %.3f ms%n", (System.nanoTime() - start) / 1e6); }
        }).fresh();
    }

    public static Crypto.P12Bundle user(Crypto.P12Bundle root, String id, String name, String title, String unit) {
        try {
            String identity = PkiCaService.certificateFingerprint(root.getEndEntityCertificate())
                    + ":" + List.of(id, Objects.toString(name, ""), Objects.toString(title, ""), Objects.toString(unit, ""));
            return MATERIALS.computeIfAbsent(identity, ignored -> {
                long start = System.nanoTime();
                try {
                    var pair = keyPair("user:" + id);
                    var subject = new X500NameBuilder(BCStyle.INSTANCE);
                    subject.addRDN(BCStyle.C, "VN");
                    subject.addRDN(BCStyle.O, "Truong Dai hoc Dien Luc - EPU");
                    if (unit != null && !unit.isBlank()) subject.addRDN(BCStyle.OU, unit);
                    if (title != null && !title.isBlank()) subject.addRDN(BCStyle.T, title);
                    subject.addRDN(BCStyle.UID, id);
                    subject.addRDN(BCStyle.CN, name == null || name.isBlank() ? "Can bo " + id : name);
                    var ca = root.getEndEntityCertificate();
                    var before = Date.from(START.minus(5, ChronoUnit.MINUTES));
                    // An expired CA needs a historical interval, not today's notBefore.
                    if (before.before(ca.getNotBefore()) || before.after(ca.getNotAfter()))
                        before = ca.getNotBefore();
                    var after = Date.from(START.plus(365, ChronoUnit.DAYS));
                    if (after.after(ca.getNotAfter())) after = ca.getNotAfter();
                    // A future CA can start after the default user expiry date.
                    if (after.before(before)) after = before;
                    var builder = new JcaX509v3CertificateBuilder(X500Name.getInstance(ca.getSubjectX500Principal().getEncoded()),
                            BigInteger.valueOf(SERIAL.incrementAndGet()), before, after, subject.build(), pair.getPublic());
                    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                    builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
                    var extensions = new JcaX509ExtensionUtils();
                    builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(pair.getPublic()));
                    builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(ca));
                    var leaf = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(
                            new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(root.privateKey())));
                    return new Material(pair.getPrivate().getEncoded().clone(), new byte[][]{leaf.getEncoded(), ca.getEncoded()});
                } catch (Exception e) { throw new IllegalStateException(e); }
                finally { System.out.printf("PKI_TEMPLATE user %.3f ms%n", (System.nanoTime() - start) / 1e6); }
            }).fresh();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static byte[] pkcs12(Crypto.P12Bundle bundle, PkiProperties properties) {
        try {
            String identity = Crypto.sha256Hex(bundle.privateKey().getEncoded()) + ":"
                    + PkiCaService.certificateFingerprint(bundle.getEndEntityCertificate()) + ":"
                    + properties.getDefaultKeystorePassword() + ":" + System.getProperty(PkiIterationExtension.PROPERTY);
            return P12.computeIfAbsent(identity, ignored -> {
                char[] password = properties.password();
                try { return Crypto.encodeP12(password, "sample", bundle.privateKey(), bundle.chain()); }
                finally { Arrays.fill(password, '\0'); }
            }).clone();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static PkiCaEntity caRecord(Crypto.P12Bundle root, PkiProperties properties, PkiEncryptionService encryption) {
        byte[] bytes = pkcs12(root, properties);
        try {
            var encrypted = encryption.encrypt(bytes);
            var record = new PkiCaEntity();
            record.setStatus(PkiCaEntity.Status.ACTIVE);
            record.setCertificatePem(Crypto.encodeCertPem(root.getEndEntityCertificate()));
            record.setFingerprintSha256(PkiCaService.certificateFingerprint(root.getEndEntityCertificate()));
            record.setKeystoreEncrypted(encrypted.ciphertext());
            record.setEncryptionNonce(encrypted.nonce());
            record.setEncryptionKeyId(encrypted.keyId());
            return record;
        } catch (Exception e) { throw new IllegalStateException(e); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    public static PkiUserKeystoreEntity userRecord(Crypto.P12Bundle user, long id, long caId,
                                                  PkiProperties properties, PkiEncryptionService encryption) {
        byte[] bytes = pkcs12(user, properties);
        try {
            var encrypted = encryption.encrypt(bytes);
            var record = new PkiUserKeystoreEntity();
            record.setUserId(id); record.setCaId(caId);
            record.setCertificatePem(Crypto.encodeCertPem(user.getEndEntityCertificate()));
            record.setCertificateSerial(user.getEndEntityCertificate().getSerialNumber().toString(16));
            record.setKeystoreEncrypted(encrypted.ciphertext());
            record.setEncryptionNonce(encrypted.nonce());
            record.setEncryptionKeyId(encrypted.keyId());
            return record;
        } finally { Arrays.fill(bytes, (byte) 0); }
    }
}

package vn.edu.epu.eoffice_be.security.pki;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executable contract for the BC version used by this project, not an assumed JDK property. */
@FastPkiTest
class Pkcs12TestSupportTest {
    private static final char[] PASSWORD = "synthetic-codec-password".toCharArray();

    static int[] counts(byte[] bytes) throws Exception {
        var pfx = Pfx.getInstance(ASN1Primitive.fromByteArray(bytes));
        var safe = AuthenticatedSafe.getInstance(ASN1Primitive.fromByteArray(
                ASN1OctetString.getInstance(pfx.getAuthSafe().getContent()).getOctets()));
        int key = -1, cert = -1;
        for (var info : safe.getContentInfo()) {
            if (info.getContentType().equals(PKCSObjectIdentifiers.data)) {
                var bags = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(
                        ASN1OctetString.getInstance(info.getContent()).getOctets()));
                for (var object : bags) {
                    var bag = SafeBag.getInstance(object);
                    if (bag.getBagId().equals(PKCSObjectIdentifiers.pkcs8ShroudedKeyBag)) {
                        var encrypted = EncryptedPrivateKeyInfo.getInstance(bag.getBagValue());
                        key = PKCS12PBEParams.getInstance(encrypted.getEncryptionAlgorithm().getParameters())
                                .getIterations().intValueExact();
                    }
                }
            } else if (info.getContentType().equals(PKCSObjectIdentifiers.encryptedData)) {
                cert = PKCS12PBEParams.getInstance(EncryptedData.getInstance(info.getContent())
                        .getEncryptionAlgorithm().getParameters()).getIterations().intValueExact();
            }
        }
        return new int[]{key, cert, pfx.getMacData().getIterationCount().intValueExact()};
    }

    private static byte[] encode(Crypto.P12Bundle bundle) {
        return Crypto.encodeP12(PASSWORD, "test", bundle.privateKey(), bundle.chain());
    }

    @Test void lowIterationsStillReadRealKeysAndRejectWrongPasswordAndCorruption() throws Exception {
        var bundle = PkiTestSamples.root();
        byte[] bytes = encode(bundle);
        assertArrayEquals(new int[]{1, 1, 2}, counts(bytes));
        assertArrayEquals(bundle.privateKey().getEncoded(), Crypto.decodeP12(bytes, PASSWORD).privateKey().getEncoded());
        assertThrows(IllegalArgumentException.class, () -> Crypto.decodeP12(bytes, "wrong".toCharArray()));
        byte[] corrupted = bytes.clone(); corrupted[corrupted.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> Crypto.decodeP12(corrupted, PASSWORD));
    }

    @Test void providerReadsPropertyAgainForEachNewStoreIncludingDefaultCompatibility() throws Exception {
        var bundle = PkiTestSamples.root();
        try (var ignored = new PkiIterationExtension.PropertyScope("7")) {
            assertArrayEquals(new int[]{7, 7, 14}, counts(encode(bundle)));
        }
        assertArrayEquals(new int[]{1, 1, 2}, counts(encode(bundle)));
        try (var ignored = new PkiIterationExtension.PropertyScope(null)) {
            byte[] bytes = encode(bundle);
            assertArrayEquals(new int[]{600000, 600000, 1200000}, counts(bytes));
            assertArrayEquals(bundle.privateKey().getEncoded(), Crypto.decodeP12(bytes, PASSWORD).privateKey().getEncoded());
        }
        assertArrayEquals(new int[]{1, 1, 2}, counts(encode(bundle)));
    }

    @Test void workerThreadsUseTheSameLowCostStoreConfiguration() throws Exception {
        var bundle = PkiTestSamples.root();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            assertArrayEquals(new int[]{1, 1, 2}, executor.submit(() -> counts(encode(bundle))).get());
        }
    }

    @Test void propertyScopeRestoresPreviousValueEvenAfterFailure() {
        assertEquals("1", System.getProperty(PkiIterationExtension.PROPERTY));
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = new PkiIterationExtension.PropertyScope("7")) {
                throw new IllegalStateException("synthetic setup failure");
            }
        });
        assertEquals("1", System.getProperty(PkiIterationExtension.PROPERTY));
    }

    @Test void bundleArraysAndKeysAreFreshCopies() {
        var first = PkiTestSamples.root();
        var second = PkiTestSamples.root();
        assertNotSame(first.privateKey(), second.privateKey());
        assertNotSame(first.chain(), second.chain());
        first.chain()[0] = null;
        assertNotNull(PkiTestSamples.root().getEndEntityCertificate());
        assertNotNull(second.getEndEntityCertificate());
    }

    @Test void recordCopiesCannotCorruptAnotherFixtureOrCachedTemplate(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var root = PkiTestSamples.root();
        var user = PkiTestSamples.user(root, "1001", "A", null, null);
        var one = PkiTestFixture.withoutCa(directory.resolve("one"));
        var two = PkiTestFixture.withoutCa(directory.resolve("two"));
        var first = PkiTestSamples.userRecord(user, 1001L, 1L, one.properties(), one.encryption());
        var second = PkiTestSamples.userRecord(user, 1001L, 1L, two.properties(), two.encryption());
        var original = second.getKeystoreEncrypted();
        byte[] broken = first.getKeystoreEncrypted(); broken[0] ^= 1; first.setKeystoreEncrypted(broken);
        first.setUserId(999L);
        assertEquals(1001L, second.getUserId());
        assertArrayEquals(original, second.getKeystoreEncrypted());
        var fresh = PkiTestSamples.userRecord(user, 1001L, 1L, two.properties(), two.encryption());
        byte[] plain = two.encryption().decrypt(new vn.edu.epu.eoffice_be.service.PkiEncryptionService.EncryptedKeystore(
                fresh.getKeystoreEncrypted(), fresh.getEncryptionNonce(), fresh.getEncryptionKeyId()));
        try {
            assertArrayEquals(user.privateKey().getEncoded(), Crypto.decodeP12(plain, two.properties().password()).privateKey().getEncoded());
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    /** Run in a fresh JVM per sample; warm-up is inside that JVM. No Surefire test is skipped. */
    public static void main(String[] args) throws Exception {
        if (args[0].equals("prepare")) {
            var sample = PkiTestSamples.root();
            var directory = java.nio.file.Path.of(args[1]);
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.write(directory.resolve("synthetic-key.der"), sample.privateKey().getEncoded());
            java.nio.file.Files.write(directory.resolve("synthetic-ca.der"), sample.getEndEntityCertificate().getEncoded());
            return;
        }
        try (var ignored = new PkiIterationExtension.PropertyScope(args[0].equals("default") ? null : "1")) {
            Crypto.P12Bundle bundle;
            if (args.length > 1) {
                // Identical synthetic DER input in every benchmark JVM; never a deployment key.
                var directory = java.nio.file.Path.of(args[1]);
                Crypto.registerBouncyCastle();
                var key = java.security.KeyFactory.getInstance("RSA").generatePrivate(
                        new java.security.spec.PKCS8EncodedKeySpec(java.nio.file.Files.readAllBytes(directory.resolve("synthetic-key.der"))));
                var cert = (java.security.cert.X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509", "BC")
                        .generateCertificate(new java.io.ByteArrayInputStream(java.nio.file.Files.readAllBytes(directory.resolve("synthetic-ca.der"))));
                bundle = new Crypto.P12Bundle(key, new java.security.cert.X509Certificate[]{cert});
            } else bundle = PkiTestSamples.root();
            byte[] warmup = encode(bundle); Crypto.decodeP12(warmup, PASSWORD);
            long encode = 0, decode = 0;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime(); byte[] bytes = encode(bundle); long middle = System.nanoTime();
                Crypto.decodeP12(bytes, PASSWORD); long end = System.nanoTime();
                encode += middle - start; decode += end - middle;
                assertArrayEquals(args[0].equals("default") ? new int[]{600000,600000,1200000} : new int[]{1,1,2}, counts(bytes));
            }
            System.out.printf("PKI_BENCH mode=%s encodeMeanMs=%.3f decodeMeanMs=%.3f%n", args[0], encode / 3e6, decode / 3e6);
        }
    }
}

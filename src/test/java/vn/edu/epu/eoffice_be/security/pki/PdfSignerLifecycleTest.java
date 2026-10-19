package vn.edu.epu.eoffice_be.security.pki;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PdfSignerLifecycleTest {
    @TempDir
    Path directory;

    @Test
    void missingCaFailsBeforeCreatingUserMaterial() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        byte[] pdf = pdf();
        BusinessException error = assertThrows(BusinessException.class,
                () -> pki.signer().signBytes(pdf, "1001", "Nguyen Van A", "Ký"));
        assertEquals(ErrorCode.PKI_CA_NOT_INITIALIZED, error.getErrorCode());
        assertFalse(Files.exists(pki.properties().caKeystore()));
        assertFalse(Files.exists(pki.properties().userKeystores()));
    }

    @Test
    void allPathSigningEntryPointsRequireCaBeforeWritingOutput() {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        PdfSigner signer = pki.signer();
        Path input = directory.resolve("input.pdf");
        Path output = directory.resolve("output.pdf");
        var operations = java.util.List.<org.junit.jupiter.api.function.Executable>of(
                () -> signer.sign(input, output, "1001", "Ký"),
                () -> signer.signVisible(input, output, "1001", "Ký", null),
                () -> signer.signAndCertify(input, output, "1001", "Ký", null));
        for (var operation : operations) {
            BusinessException error = assertThrows(BusinessException.class, operation);
            assertEquals(ErrorCode.PKI_CA_NOT_INITIALIZED, error.getErrorCode());
        }
        assertFalse(Files.exists(output));
    }

    @Test
    void firstSignCreatesKeysAndNewInstanceReusesTheSameBytes() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        byte[] ca = Files.readAllBytes(pki.properties().caKeystore());
        var first = pki.signer().signBytes(pdf(), "1001", "Nguyen Van A", "Ký lần đầu");
        Path keystore = userP12(pki, "1001");
        Path certificate = userCertificate(pki, "1001");
        byte[] originalP12 = Files.readAllBytes(keystore);
        byte[] originalCertificate = Files.readAllBytes(certificate);
        Path staleTemporary = pki.properties().userKeystores().resolve(".user_1001.p12.cleanup.tmp");
        Files.writeString(staleTemporary, "old temporary material");
        var second = pki.signer().signBytes(pdf(), "1001", "Tên thay đổi", "Ký tài liệu khác");
        assertEquals(first.serialHex(), second.serialHex());
        assertEquals("Nguyen Van A", second.signerCN());
        assertArrayEquals(originalP12, Files.readAllBytes(keystore));
        assertArrayEquals(originalCertificate, Files.readAllBytes(certificate));
        assertArrayEquals(ca, Files.readAllBytes(pki.properties().caKeystore()));
        assertEquals("old temporary material", Files.readString(staleTemporary));
        assertEquals("VALID", pki.verifier().verify(second.signedPdfBytes()).getOverallStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {".user_1001.p12.interrupted.tmp", ".user_1001.crt.interrupted.tmp",
            "user_1001.p12.tmp", "user_1001.crt.tmp"})
    void interruptedUserMaterialBlocksNewIssuanceWithoutRemovingEvidence(String temporaryName) throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        Files.createDirectories(pki.properties().userKeystores());
        Path interrupted = pki.properties().userKeystores().resolve(temporaryName);
        Files.writeString(interrupted, "interrupted key material");
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertEquals("interrupted key material", Files.readString(interrupted));
        assertFalse(Files.exists(userP12(pki, "1001")));
        assertFalse(Files.exists(userCertificate(pki, "1001")));
    }

    @Test
    void anotherUsersInterruptedMaterialDoesNotBlockFirstSign() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        Files.createDirectories(pki.properties().userKeystores());
        Path interrupted = pki.properties().userKeystores().resolve(".user_10010.p12.interrupted.tmp");
        Files.writeString(interrupted, "another user's material");
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        assertTrue(Files.exists(userP12(pki, "1001")));
        assertTrue(Files.exists(userCertificate(pki, "1001")));
        assertEquals("another user's material", Files.readString(interrupted));
    }

    @Test
    void missingUserKeysWithSigningHistoryRequiresRecovery() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        when(pki.history().hasUserSigningHistory("1001")).thenReturn(true);
        BusinessException error = assertThrows(BusinessException.class,
                () -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertEquals(ErrorCode.PKI_USER_KEYSTORE_INVALID, error.getErrorCode());
        assertFalse(Files.exists(userP12(pki, "1001")));
        assertFalse(Files.exists(userCertificate(pki, "1001")));
    }

    @Test
    void unavailableSigningHistoryFailsClosedWithoutIssuingKeys() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        when(pki.history().hasUserSigningHistory("1001"))
                .thenThrow(new DataAccessResourceFailureException("history unavailable"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertEquals(ErrorCode.PKI_STORAGE_ERROR, error.getErrorCode());
        assertFalse(Files.exists(userP12(pki, "1001")));
        assertFalse(Files.exists(userCertificate(pki, "1001")));
    }

    @Test
    void orphanCertificateAndCorruptKeystoreAreNeverReplaced() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        Files.createDirectories(pki.properties().userKeystores());
        Path certificate = userCertificate(pki, "1001");
        Files.writeString(certificate, "original orphan certificate");
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertFalse(Files.exists(userP12(pki, "1001")));
        assertEquals("original orphan certificate", Files.readString(certificate));

        Path p12 = userP12(pki, "1001");
        Files.writeString(p12, "original corrupt keystore");
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertEquals("original corrupt keystore", Files.readString(p12));
        assertEquals("original orphan certificate", Files.readString(certificate));
    }

    @Test
    void existingUserKeysStillRequireTheConfiguredCa() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        byte[] original = Files.readAllBytes(userP12(pki, "1001"));
        Files.delete(pki.properties().caKeystore());
        byte[] pdf = pdf();
        BusinessException error = assertThrows(BusinessException.class,
                () -> pki.signer().signBytes(pdf, "1001", "Nguyen Van A", "Ký"));
        assertEquals(ErrorCode.PKI_CA_RECOVERY_REQUIRED, error.getErrorCode());
        assertArrayEquals(original, Files.readAllBytes(userP12(pki, "1001")));
    }

    @Test
    void uidAndIssuerMismatchAreRejectedWithoutReplacingKeys() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory.resolve("first"));
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        byte[] original = Files.readAllBytes(userP12(pki, "1001"));
        Files.copy(userP12(pki, "1001"), userP12(pki, "1002"));
        Files.copy(userCertificate(pki, "1001"), userCertificate(pki, "1002"));
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1002", "Tran Thi B"));
        assertArrayEquals(original, Files.readAllBytes(userP12(pki, "1002")));

        PkiTestFixture other = PkiTestFixture.create(directory.resolve("second"));
        Files.createDirectories(other.properties().userKeystores());
        Files.copy(userP12(pki, "1001"), userP12(other, "1001"));
        Files.copy(userCertificate(pki, "1001"), userCertificate(other, "1001"));
        assertUserInvalid(() -> other.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertArrayEquals(original, Files.readAllBytes(userP12(other, "1001")));
    }

    @Test
    void privateKeyMismatchIsRejectedWithoutReplacingMaterial() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        char[] password = pki.properties().password();
        var bundle = Crypto.loadP12(userP12(pki, "1001"), password);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var unrelated = generator.generateKeyPair();
        Crypto.saveP12(userP12(pki, "1001"), password, "user", unrelated.getPrivate(), bundle.chain());
        java.util.Arrays.fill(password, '\0');
        byte[] original = Files.readAllBytes(userP12(pki, "1001"));
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertArrayEquals(original, Files.readAllBytes(userP12(pki, "1001")));
    }

    @Test
    void mismatchedPublicCertificateIsNotReplaced() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        byte[] unrelated = Files.readAllBytes(pki.properties().caCertificate());
        Files.write(userCertificate(pki, "1001"), unrelated);
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertArrayEquals(unrelated, Files.readAllBytes(userCertificate(pki, "1001")));
    }

    @Test
    void unusableUserDirectoryReturnsTypedStorageError() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        Files.writeString(pki.properties().userKeystores(), "not a directory");
        BusinessException error = assertThrows(BusinessException.class,
                () -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertEquals(ErrorCode.PKI_STORAGE_ERROR, error.getErrorCode());
        assertEquals("not a directory", Files.readString(pki.properties().userKeystores()));
    }

    @Test
    void userCertificateCannotOutliveItsIssuer() throws Exception {
        Instant now = Instant.now();
        PkiTestFixture pki = PkiTestFixture.create(directory,
                now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS));
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        var ca = pki.caService().requireValidCa().getEndEntityCertificate();
        var user = Crypto.loadCertPem(userCertificate(pki, "1001"));
        assertEquals(ca.getNotAfter(), user.getNotAfter());
        assertFalse(user.getNotBefore().before(ca.getNotBefore()));
    }

    @Test
    void expiredUserCertificateIsNotAutomaticallyRenewed() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
        char[] password = pki.properties().password();
        var user = Crypto.loadP12(userP12(pki, "1001"), password);
        var ca = pki.caService().requireValidCa();
        var leaf = user.getEndEntityCertificate();
        Instant now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                X500Name.getInstance(ca.getEndEntityCertificate().getSubjectX500Principal().getEncoded()),
                BigInteger.TEN, Date.from(now.minus(2, ChronoUnit.DAYS)), Date.from(now.minus(1, ChronoUnit.DAYS)),
                X500Name.getInstance(leaf.getSubjectX500Principal().getEncoded()), leaf.getPublicKey());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        var signingKey = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(ca.privateKey());
        var expired = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signingKey));
        Crypto.saveP12(userP12(pki, "1001"), password, "user", user.privateKey(),
                new Certificate[]{expired, ca.getEndEntityCertificate()});
        java.util.Arrays.fill(password, '\0');
        Crypto.saveCertPem(userCertificate(pki, "1001"), expired);
        byte[] original = Files.readAllBytes(userP12(pki, "1001"));
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "Nguyen Van A"));
        assertArrayEquals(original, Files.readAllBytes(userP12(pki, "1001")));
    }

    @Test
    void twoSignerInstancesProvisionOneKeyPair() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(() -> {
                    pki.signer().ensureUserCertificate("1001", "Nguyen Van A");
                    return certificateFingerprint(userCertificate(pki, "1001"));
                });
            }
            var results = executor.invokeAll(tasks);
            assertEquals(results.get(0).get(), results.get(1).get());
        }
    }

    private String certificateFingerprint(Path certificate) throws Exception {
        return vn.edu.epu.eoffice_be.service.PkiCaService.certificateFingerprint(Crypto.loadCertPem(certificate));
    }

    private void assertUserInvalid(org.junit.jupiter.api.function.Executable operation) {
        BusinessException error = assertThrows(BusinessException.class, operation);
        assertEquals(ErrorCode.PKI_USER_KEYSTORE_INVALID, error.getErrorCode());
    }

    private Path userP12(PkiTestFixture pki, String userId) {
        return pki.properties().userKeystores().resolve("user_" + userId + ".p12");
    }

    private Path userCertificate(PkiTestFixture pki, String userId) {
        return pki.properties().userKeystores().resolve("user_" + userId + ".crt");
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}

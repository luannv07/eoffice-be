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
import vn.edu.epu.eoffice_be.entity.PkiUserKeystoreEntity;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfSignerLifecycleTest {
    @TempDir Path directory;

    @Test
    void missingCaFailsBeforeCreatingUserMaterial() throws Exception {
        var pki = PkiTestFixture.withoutCa(directory);
        assertError(ErrorCode.PKI_CA_NOT_INITIALIZED, () -> pki.signer().signBytes(pdf(), "1001", "A", "Sign"));
        verify(pki.userRepository(), never()).saveAndFlush(any());
        assertFalse(Files.exists(pki.properties().userKeystores()));
    }

    @Test
    void allPathSigningEntryPointsRequireCaBeforeWritingOutput() {
        var pki = PkiTestFixture.withoutCa(directory);
        Path input = directory.resolve("input.pdf"), output = directory.resolve("output.pdf");
        var signer = pki.signer();
        assertError(ErrorCode.PKI_CA_NOT_INITIALIZED, () -> signer.sign(input, output, "1001", "Sign"));
        assertError(ErrorCode.PKI_CA_NOT_INITIALIZED, () -> signer.signVisible(input, output, "1001", "Sign", null));
        assertError(ErrorCode.PKI_CA_NOT_INITIALIZED, () -> signer.signAndCertify(input, output, "1001", "Sign", null));
        assertFalse(Files.exists(output));
    }

    @Test
    void firstSignPersistsOneEncryptedKeystoreAndNewInstanceReusesItWithoutFiles() throws Exception {
        var pki = PkiTestFixture.create(directory);
        var first = pki.signer().signBytes(pdf(), "1001", "Nguyen Van A", "First");
        var record = record(pki);
        byte[] encrypted = record.getKeystoreEncrypted().clone();
        var second = pki.signer().signBytes(pdf(), "1001", "Changed name", "Second");
        assertEquals(first.serialHex(), second.serialHex());
        assertEquals("Nguyen Van A", second.signerCN());
        assertEquals(first.serialHex(), record.getCertificateSerial());
        assertEquals(1001L, record.getUserId());
        assertEquals(1L, record.getCaId());
        assertArrayEquals(encrypted, record.getKeystoreEncrypted());
        verify(pki.userRepository(), times(1)).saveAndFlush(any());
        assertEquals(1L, pki.userRepository().count());
        assertFalse(Files.exists(pki.properties().userKeystores()));
        assertEquals("VALID", pki.verifier().verify(first.signedPdfBytes()).getOverallStatus());
        assertEquals("VALID", pki.verifier().verify(second.signedPdfBytes()).getOverallStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_1001.p12", "user_1001.crt", ".user_1001.p12.interrupted.tmp", "user_1001.crt.tmp"})
    void legacyUserFilesBlockIssuanceWithoutRemovingEvidence(String filename) throws Exception {
        var pki = PkiTestFixture.create(directory);
        Files.createDirectories(pki.properties().userKeystores());
        var evidence = pki.properties().userKeystores().resolve(filename);
        Files.writeString(evidence, "old key evidence");
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "A"));
        assertEquals("old key evidence", Files.readString(evidence));
        verify(pki.userRepository(), never()).saveAndFlush(any());
    }

    @Test
    void anotherUsersLegacyFilesDoNotBlockIssuance() throws Exception {
        var pki = PkiTestFixture.create(directory);
        Files.createDirectories(pki.properties().userKeystores());
        Files.writeString(pki.properties().userKeystores().resolve("user_10010.p12"), "other user");
        pki.signer().ensureUserCertificate("1001", "A");
        assertNotNull(record(pki));
    }

    @Test
    void missingKeysWithSigningHistoryRequireRecovery() throws Exception {
        var pki = PkiTestFixture.create(directory);
        when(pki.history().hasUserSigningHistory("1001")).thenReturn(true);
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "A"));
        verify(pki.userRepository(), never()).saveAndFlush(any());
    }

    @Test
    void missingOwnerAndInvalidUserIdsCannotIssueKeys() throws Exception {
        var pki = PkiTestFixture.create(directory);
        when(pki.users().lockForPkiIssuance(1001L)).thenReturn(Optional.empty());
        assertError(ErrorCode.BAD_REQUEST, () -> pki.signer().ensureUserCertificate("1001", "A"));
        for (String invalid : new String[]{"../1", "0", "-1", "01", "abc", "99999999999999999999999"}) {
            assertError(ErrorCode.BAD_REQUEST, () -> pki.signer().ensureUserCertificate(invalid, "A"));
        }
        verify(pki.userRepository(), never()).saveAndFlush(any());
    }

    @Test
    void unavailableHistoryAndDatabaseFailClosed() throws Exception {
        var pki = PkiTestFixture.create(directory);
        when(pki.history().hasUserSigningHistory("1001")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertError(ErrorCode.PKI_STORAGE_ERROR, () -> pki.signer().ensureUserCertificate("1001", "A"));
        when(pki.userRepository().findByUserIdAndCaId(1001L, 1L)).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertError(ErrorCode.PKI_STORAGE_ERROR, () -> pki.userService().require("1001"));
        verify(pki.userRepository(), never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ciphertext", "nonce", "key-id", "pem", "serial", "owner", "ca", "password"})
    void corruptUserRecordIsNeverReplaced(String damage) throws Exception {
        var pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "A");
        var record = record(pki);
        switch (damage) {
            case "ciphertext" -> {
                byte[] changed = record.getKeystoreEncrypted();
                changed[0] ^= 1;
                record.setKeystoreEncrypted(changed);
            }
            case "nonce" -> {
                byte[] changed = record.getEncryptionNonce();
                changed[0] ^= 1;
                record.setEncryptionNonce(changed);
            }
            case "key-id" -> record.setEncryptionKeyId("unknown");
            case "pem" -> record.setCertificatePem("broken certificate");
            case "serial" -> record.setCertificateSerial("incorrect");
            case "owner" -> record.setUserId(1002L);
            case "ca" -> record.setCaId(999L);
            case "password" -> {
                var bundle = pki.userService().require("1001");
                byte[] plaintext = Crypto.encodeP12("different-password".toCharArray(), "user", bundle.privateKey(), bundle.chain());
                try { replaceEncrypted(pki, record, plaintext); }
                finally { java.util.Arrays.fill(plaintext, (byte) 0); }
            }
        }
        byte[] original = record.getKeystoreEncrypted().clone();
        clearInvocations(pki.userRepository());
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "A"));
        assertUserInvalid(() -> pki.userService().require("1001"));
        verify(pki.userRepository(), never()).saveAndFlush(any());
        assertArrayEquals(original, record.getKeystoreEncrypted());
    }

    @Test
    void storedKeysStillRequireValidCa() throws Exception {
        var pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "A");
        pki.repository().findActive().orElseThrow().setKeystoreEncrypted(new byte[17]);
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, () -> pki.userService().require("1001"));
    }

    @Test
    void userCertificateCannotOutliveCa() throws Exception {
        Instant now = Instant.now();
        var pki = PkiTestFixture.create(directory, now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS));
        pki.signer().ensureUserCertificate("1001", "A");
        var root = pki.caService().requireValidCa().getEndEntityCertificate();
        var leaf = pki.userService().require("1001").getEndEntityCertificate();
        assertEquals(root.getNotAfter(), leaf.getNotAfter());
        assertFalse(leaf.getNotBefore().before(root.getNotBefore()));
    }

    @Test
    void privateKeyMismatchIsRejected() throws Exception {
        var pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "A");
        var bundle = pki.userService().require("1001");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        char[] password = pki.properties().password();
        byte[] plaintext = Crypto.encodeP12(password, "user", generator.generateKeyPair().getPrivate(), bundle.chain());
        try { replaceEncrypted(pki, record(pki), plaintext); }
        finally { java.util.Arrays.fill(password, '\0'); java.util.Arrays.fill(plaintext, (byte) 0); }
        assertUserInvalid(() -> pki.userService().require("1001"));
    }

    @Test
    void issuerAndUidMismatchAreRejected() throws Exception {
        var pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "A");
        var original = record(pki);
        var wrongOwner = new PkiUserKeystoreEntity();
        wrongOwner.setUserId(1002L); wrongOwner.setCaId(1L);
        wrongOwner.setCertificatePem(original.getCertificatePem()); wrongOwner.setCertificateSerial(original.getCertificateSerial());
        wrongOwner.setKeystoreEncrypted(original.getKeystoreEncrypted()); wrongOwner.setEncryptionNonce(original.getEncryptionNonce());
        wrongOwner.setEncryptionKeyId(original.getEncryptionKeyId());
        when(pki.userRepository().findByUserIdAndCaId(1002L, 1L)).thenReturn(Optional.of(wrongOwner));
        assertUserInvalid(() -> pki.userService().require("1002"));
        var other = PkiTestFixture.create(directory.resolve("other"));
        when(other.userRepository().findByUserIdAndCaId(1001L, 1L)).thenReturn(Optional.of(original));
        assertUserInvalid(() -> other.userService().require("1001"));
    }

    @Test
    void expiredCertificateIsNotAutomaticallyRenewed() throws Exception {
        var pki = PkiTestFixture.create(directory);
        pki.signer().ensureUserCertificate("1001", "A");
        var user = pki.userService().require("1001");
        var ca = pki.caService().requireValidCa();
        var leaf = user.getEndEntityCertificate();
        Instant now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                X500Name.getInstance(ca.getEndEntityCertificate().getSubjectX500Principal().getEncoded()), BigInteger.TEN,
                Date.from(now.minus(2, ChronoUnit.DAYS)), Date.from(now.minus(1, ChronoUnit.DAYS)),
                X500Name.getInstance(leaf.getSubjectX500Principal().getEncoded()), leaf.getPublicKey());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        var expired = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(ca.privateKey())));
        char[] password = pki.properties().password();
        byte[] plaintext = Crypto.encodeP12(password, "user", user.privateKey(), new Certificate[]{expired, ca.getEndEntityCertificate()});
        try { replaceEncrypted(pki, record(pki), plaintext); }
        finally { java.util.Arrays.fill(password, '\0'); java.util.Arrays.fill(plaintext, (byte) 0); }
        record(pki).setCertificatePem(Crypto.encodeCertPem(expired));
        record(pki).setCertificateSerial(expired.getSerialNumber().toString(16));
        clearInvocations(pki.userRepository());
        assertUserInvalid(() -> pki.signer().ensureUserCertificate("1001", "A"));
        verify(pki.userRepository(), never()).saveAndFlush(any());
    }

    private void replaceEncrypted(PkiTestFixture pki, PkiUserKeystoreEntity record, byte[] plaintext) {
        var encrypted = pki.encryption().encrypt(plaintext);
        record.setKeystoreEncrypted(encrypted.ciphertext()); record.setEncryptionNonce(encrypted.nonce());
        record.setEncryptionKeyId(encrypted.keyId());
    }
    private PkiUserKeystoreEntity record(PkiTestFixture pki) { return pki.userRepository().findByUserIdAndCaId(1001L, 1L).orElseThrow(); }
    private void assertUserInvalid(org.junit.jupiter.api.function.Executable action) { assertError(ErrorCode.PKI_USER_KEYSTORE_INVALID, action); }
    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).getErrorCode());
    }
    private byte[] pdf() throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); return output.toByteArray();
        }
    }
}

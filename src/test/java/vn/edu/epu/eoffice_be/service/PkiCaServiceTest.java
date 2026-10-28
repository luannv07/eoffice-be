package vn.edu.epu.eoffice_be.service;

import vn.edu.epu.eoffice_be.security.pki.Crypto;
import vn.edu.epu.eoffice_be.security.pki.PkiTestSamples;
import vn.edu.epu.eoffice_be.security.pki.PkiSampleFixtures;
import vn.edu.epu.eoffice_be.security.pki.FastPkiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse.State;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse.Reason;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;
import vn.edu.epu.eoffice_be.security.pki.PkiTestFixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@FastPkiTest
class PkiCaServiceTest {
    @TempDir Path directory;

    @Test
    void emptyDatabaseAllowsExplicitInitializationOnly() {
        var pki = PkiTestFixture.withoutCa(directory);
        assertEquals(State.NOT_INITIALIZED, pki.caService().status().state());
        assertTrue(pki.caService().status().canInitialize());
        assertError(ErrorCode.PKI_CA_NOT_INITIALIZED, pki.caService()::requireValidCa);
        verify(pki.repository(), never()).saveAndFlush(any());
        assertFalse(Files.exists(pki.properties().caKeystore()));
    }

    @Test
    void initializationPersistsEncryptedCaAndFreshServiceReadsSameKeyWithoutFiles() throws Exception {
        var pki = PkiTestFixture.withoutCa(directory);
        var created = pki.caService().initialize();
        assertEquals(State.READY, created.state());
        assertFalse(created.canInitialize());
        var record = pki.repository().findActive().orElseThrow();
        assertEquals(created.fingerprintSha256(), record.getFingerprintSha256());
        byte[] originalCiphertext = record.getKeystoreEncrypted().clone();
        var first = pki.caService().requireValidCa();
        assertEquals(3072, ((java.security.interfaces.RSAPublicKey) first.getEndEntityCertificate().getPublicKey()).getModulus().bitLength());
        var restarted = new PkiCaService(pki.properties(), pki.history(), pki.repository(),
                new PkiEncryptionService(pki.encryptionProperties()));
        assertEquals(created.fingerprintSha256(), restarted.status().fingerprintSha256());
        assertArrayEquals(first.privateKey().getEncoded(), restarted.requireValidCa().privateKey().getEncoded());
        assertArrayEquals(originalCiphertext, record.getKeystoreEncrypted());
        assertFalse(Files.exists(pki.properties().caKeystore()));
        assertFalse(Files.exists(pki.properties().caCertificate()));
        assertFalse(Files.exists(pki.properties().revocations()));
        assertFalse(Files.exists(pki.properties().userKeystores()));
        assertError(ErrorCode.PKI_CA_ALREADY_EXISTS, restarted::initialize);
        verify(pki.repository(), times(1)).saveAndFlush(any());
    }

    @Test
    void missingCaWithSigningHistoryOrUserRecordsBlocksNewCa() {
        var pki = PkiTestFixture.withoutCa(directory);
        when(pki.history().hasAnySigningHistory()).thenReturn(true);
        assertEquals(Reason.CA_RECORD_MISSING_WITH_SIGNING_HISTORY, pki.caService().status().diagnostics().reason());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::initialize);
        when(pki.repository().countUserKeystores()).thenReturn(1L);
        assertEquals(Reason.CA_RECORD_MISSING_WITH_USER_KEYSTORES, pki.caService().status().diagnostics().reason());
        verify(pki.repository(), never()).saveAndFlush(any());
    }

    @Test
    void retiredCaCannotBeSilentlyReplaced() throws Exception {
        var pki = PkiSampleFixtures.create(directory);
        pki.repository().findActive().orElseThrow().setStatus(PkiCaEntity.Status.RETIRED);
        assertEquals(Reason.NO_ACTIVE_CA, pki.caService().status().diagnostics().reason());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::initialize);
    }

    @Test
    void legacyKeysBlockInitializationAndArePreserved() throws Exception {
        var pki = PkiTestFixture.withoutCa(directory);
        Files.createDirectories(pki.properties().caKeystore().getParent());
        Files.writeString(pki.properties().caKeystore(), "existing key evidence");
        assertEquals(Reason.LEGACY_PKI_REQUIRES_MIGRATION, pki.caService().status().diagnostics().reason());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::initialize);
        assertEquals("existing key evidence", Files.readString(pki.properties().caKeystore()));
        verify(pki.repository(), never()).saveAndFlush(any());
    }

    @Test
    void interruptedCaMaterialAlsoBlocksInitialization() throws Exception {
        var pki = PkiTestFixture.withoutCa(directory);
        Files.createDirectories(pki.properties().caKeystore().getParent());
        Path interrupted = pki.properties().caKeystore().resolveSibling(".ca.p12.interrupted.tmp");
        Files.writeString(interrupted, "interrupted material");
        assertEquals(Reason.LEGACY_PKI_REQUIRES_MIGRATION, pki.caService().status().diagnostics().reason());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::initialize);
        assertEquals("interrupted material", Files.readString(interrupted));
    }

    @Test
    void mismatchedPrivateKeyIsRejectedEvenWhenCertificateAndFingerprintMatch() throws Exception {
        var pki = PkiSampleFixtures.create(directory);
        var original = pki.caService().requireValidCa();
        var otherKey = PkiTestSamples.keyPair("mismatched-ca");
        char[] password = pki.properties().password();
        byte[] plaintext = Crypto.encodeP12(password, "mismatched",
                otherKey.getPrivate(), original.chain());
        try {
            var encrypted = pki.encryption().encrypt(plaintext);
            var record = pki.repository().findActive().orElseThrow();
            record.setKeystoreEncrypted(encrypted.ciphertext());
            record.setEncryptionNonce(encrypted.nonce());
            assertEquals(Reason.CA_KEY_CERTIFICATE_MISMATCH, pki.caService().status().diagnostics().reason());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ciphertext", "nonce", "aes", "key-id", "password", "pem", "fingerprint", "certificate"})
    void invalidStoredMaterialRequiresRecoveryAndIsNeverReplaced(String damage) throws Exception {
        var pki = PkiSampleFixtures.create(directory);
        assertEquals(State.READY, pki.caService().status().state());
        var record = pki.repository().findActive().orElseThrow();
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
            case "aes" -> {
                byte[] wrong = new byte[32]; Arrays.fill(wrong, (byte) 1);
                pki.encryptionProperties().setKey(Base64.getEncoder().encodeToString(wrong));
            }
            case "key-id" -> record.setEncryptionKeyId("unknown-key");
            case "password" -> pki.properties().setDefaultKeystorePassword("wrong-password");
            case "pem" -> record.setCertificatePem("invalid certificate");
            case "fingerprint" -> record.setFingerprintSha256("0".repeat(64));
            case "certificate" -> record.setCertificatePem(PkiSampleFixtures.otherCa(directory.resolve("other"))
                    .repository().findActive().orElseThrow().getCertificatePem());
        }
        byte[] original = record.getKeystoreEncrypted().clone();
        clearInvocations(pki.repository());
        var status = pki.caService().status();
        assertEquals(State.RECOVERY_REQUIRED, status.state());
        Reason expectedReason = switch (damage) {
            case "pem" -> Reason.CA_CERTIFICATE_UNREADABLE;
            case "fingerprint" -> Reason.CA_FINGERPRINT_MISMATCH;
            case "certificate" -> Reason.CA_CERTIFICATE_MISMATCH;
            default -> Reason.CA_KEYSTORE_UNREADABLE;
        };
        assertEquals(expectedReason, status.diagnostics().reason());
        assertTrue(status.diagnostics().caRecordExists());
        assertFalse(status.canInitialize());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::requireValidCa);
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::initialize);
        verify(pki.repository(), never()).saveAndFlush(any());
        assertArrayEquals(original, record.getKeystoreEncrypted());
    }

    @Test
    void publicVerificationDoesNotNeedPrivateKeyOrEncryptionConfiguration() throws Exception {
        var pki = PkiSampleFixtures.create(directory);
        var certificate = pki.caService().activeCertificate().orElseThrow();
        pki.encryptionProperties().setKey(null);
        pki.repository().findActive().orElseThrow().setKeystoreEncrypted(new byte[17]);
        assertArrayEquals(certificate.getEncoded(), pki.caService().activeCertificate().orElseThrow().getEncoded());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::requireValidCa);
    }

    @Test
    void expirationAndFutureValidityAreEnforced() throws Exception {
        Instant now = Instant.now();
        var expired = PkiSampleFixtures.create(directory.resolve("expired"), now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        assertEquals(State.EXPIRED, expired.caService().status().state());
        assertError(ErrorCode.PKI_CA_EXPIRED, expired.caService()::requireValidCa);
        assertError(ErrorCode.PKI_CA_ALREADY_EXISTS, expired.caService()::initialize);
        var future = PkiSampleFixtures.create(directory.resolve("future"), now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        assertEquals(Reason.CA_NOT_YET_VALID, future.caService().status().diagnostics().reason());
    }

    @Test
    void databaseAndHistoryFailuresFailClosed() {
        var pki = PkiTestFixture.withoutCa(directory);
        when(pki.history().hasAnySigningHistory()).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::status);
        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::initialize);
        when(pki.repository().findActive()).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::requireValidCa);
        verify(pki.repository(), never()).saveAndFlush(any());
    }

    @Test
    void missingEncryptionConfigurationOrDefaultPasswordDoesNotPersistCa() {
        var pki = PkiTestFixture.withoutCa(directory);
        pki.encryptionProperties().setKey(null);
        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::initialize);
        verify(pki.repository(), never()).saveAndFlush(any());
        pki.properties().setDefaultKeystorePassword("changeit");
        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::initialize);
    }

    @Test
    void concurrentInitializationKeepsOnlyWinningCa() throws Exception {
        var pki = PkiTestFixture.withoutCa(directory);
        var other = new PkiCaService(pki.properties(), pki.history(), pki.repository(), pki.encryption());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(start, pki.caService()));
            var second = executor.submit(() -> attempt(start, other));
            start.countDown();
            assertEquals(1, first.get() + second.get());
        }
        assertEquals(State.READY, pki.caService().status().state());
    }

    private int attempt(CountDownLatch start, PkiCaService service) throws InterruptedException {
        start.await();
        try { service.initialize(); return 1; }
        catch (BusinessException e) { assertEquals(ErrorCode.PKI_CA_ALREADY_EXISTS, e.getErrorCode()); return 0; }
    }

    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).getErrorCode());
    }
}

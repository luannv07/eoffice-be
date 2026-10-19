package vn.edu.epu.eoffice_be.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.x509.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse.State;
import vn.edu.epu.eoffice_be.security.pki.Crypto;
import vn.edu.epu.eoffice_be.security.pki.PkiTestFixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PkiCaServiceTest {

    @TempDir
    Path directory;

    @Test
    void statusOfAnEmptyStoreDoesNotCreateAnyFilesOrDirectories() throws Exception {
        Path store = directory.resolve("untouched");
        PkiTestFixture pki = PkiTestFixture.withoutCa(store);

        PkiCaStatusResponse status = pki.caService().status();

        assertEquals(State.NOT_INITIALIZED, status.state());
        assertTrue(status.canInitialize());
        assertNull(status.fingerprintSha256());
        assertFalse(Files.exists(store));
        try (var entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void statusOfAReadyStoreReturnsPublicMetadataWithoutWriting() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        Map<String, byte[]> before = snapshot(directory);
        List<Path> entriesBefore = entries(directory);

        PkiCaStatusResponse status = pki.caService().status();

        assertEquals(State.READY, status.state());
        assertFalse(status.canInitialize());
        assertNotNull(status.subject());
        assertTrue(status.fingerprintSha256().matches("[0-9a-f]{64}"));
        assertNotNull(status.notBefore());
        assertNotNull(status.notAfter());
        assertEquals(entriesBefore, entries(directory));
        assertUnchanged(before, directory);
    }

    @Test
    void initializationCreatesTheRequiredCaAndCanSignAndVerifyARealPdf() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        Instant started = Instant.now();

        PkiCaStatusResponse status = pki.caService().initialize();

        assertEquals(State.READY, status.state());
        assertFalse(status.canInitialize());
        assertEquals("[]", Files.readString(pki.properties().revocations()));
        assertTrue(Files.isDirectory(pki.properties().userKeystores()));
        var ca = pki.caService().requireValidCa();
        var certificate = ca.getEndEntityCertificate();
        assertEquals(3072, ((RSAPublicKey) certificate.getPublicKey()).getModulus().bitLength());
        assertEquals(3, certificate.getVersion());
        assertEquals(0, certificate.getBasicConstraints());
        assertTrue(certificate.getKeyUsage()[5]);
        assertTrue(certificate.getKeyUsage()[6]);
        assertTrue(certificate.getCriticalExtensionOIDs().contains(Extension.basicConstraints.getId()));
        assertTrue(certificate.getCriticalExtensionOIDs().contains(Extension.keyUsage.getId()));
        assertEquals(certificate.getSubjectX500Principal(), certificate.getIssuerX500Principal());
        assertTrue(certificate.getSubjectX500Principal().getName().contains("CN=E-Office Demo Root CA"));
        assertFalse(certificate.getNotBefore().toInstant().isAfter(started));
        assertTrue(certificate.getNotAfter().toInstant().isAfter(started.plus(3649, ChronoUnit.DAYS)));
        assertTrue(certificate.getNotAfter().toInstant().isBefore(Instant.now().plus(3651, ChronoUnit.DAYS)));
        certificate.verify(certificate.getPublicKey());
        assertEquals(PkiCaService.certificateFingerprint(certificate), status.fingerprintSha256());

        char[] password = pki.properties().password();
        try (var input = Files.newInputStream(pki.properties().caKeystore())) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
            keyStore.load(input, password);
            assertTrue(keyStore.isKeyEntry("eoffice-root-ca"));
        } finally {
            Arrays.fill(password, '\0');
        }

        byte[] originalCa = Files.readAllBytes(pki.properties().caKeystore());
        var signed = pki.signer().signBytes(pdf(), "1001", "Nguyen Van A", "Ký thử nghiệm");
        var verification = pki.verifier().verify(signed.signedPdfBytes());
        assertEquals("VALID", verification.getOverallStatus());
        assertEquals(1, verification.getTotalSignatures());
        assertTrue(Files.isRegularFile(pki.properties().userKeystores().resolve("user_1001.p12")));
        assertArrayEquals(originalCa, Files.readAllBytes(pki.properties().caKeystore()));
    }

    @Test
    void aSecondInitializationRejectsAnExistingCaWithoutChangingAnyMaterial() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        pki.caService().initialize();
        Map<String, byte[]> before = snapshot(directory);

        assertError(ErrorCode.PKI_CA_ALREADY_EXISTS, pki.caService()::initialize);

        assertUnchanged(before, directory);
    }

    @Test
    void recoveryStatusStillShowsReadablePublicCertificateMetadata() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(directory);
        String fingerprint = pki.caService().status().fingerprintSha256();
        Files.delete(pki.properties().caKeystore());

        PkiCaStatusResponse status = pki.caService().status();

        assertEquals(State.RECOVERY_REQUIRED, status.state());
        assertFalse(status.canInitialize());
        assertEquals(fingerprint, status.fingerprintSha256());
        assertNotNull(status.subject());
        assertNotNull(status.notBefore());
        assertNotNull(status.notAfter());
    }

    @Test
    void conflictingStoragePathsFailBeforePublishingCaFiles() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        pki.properties().setUserKeystoresDir(pki.properties().getRevokedPath());

        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::initialize);

        assertFalse(Files.exists(pki.properties().caKeystore()));
        assertFalse(Files.exists(pki.properties().caCertificate()));
        assertFalse(Files.exists(pki.properties().revocations()));
    }

    @Test
    void expiredCaIsReportedAndNeverReinitialized() throws Exception {
        Instant now = Instant.now();
        PkiTestFixture pki = PkiTestFixture.create(directory,
                now.minus(3, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS));
        Map<String, byte[]> before = snapshot(directory);

        PkiCaStatusResponse status = pki.caService().status();
        assertEquals(State.EXPIRED, status.state());
        assertFalse(status.canInitialize());
        assertNotNull(status.fingerprintSha256());
        assertError(ErrorCode.PKI_CA_EXPIRED, pki.caService()::requireValidCa);
        assertError(ErrorCode.PKI_CA_ALREADY_EXISTS, pki.caService()::initialize);

        assertUnchanged(before, directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "only-p12", "only-crt", "corrupt-p12", "corrupt-crt", "mismatched-crt", "wrong-password",
            "missing-revocations", "corrupt-revocations", "invalid-revocation-serial", "future-ca"
    })
    void incompleteOrInvalidCaRequiresRecoveryWithoutReplacingFiles(String scenario) throws Exception {
        Path store = directory.resolve("store");
        PkiTestFixture pki = scenario.equals("future-ca")
                ? PkiTestFixture.create(store, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(2, ChronoUnit.DAYS))
                : PkiTestFixture.create(store);
        switch (scenario) {
            case "only-p12" -> Files.delete(pki.properties().caCertificate());
            case "only-crt" -> Files.delete(pki.properties().caKeystore());
            case "corrupt-p12" -> Files.writeString(pki.properties().caKeystore(), "original corrupt p12");
            case "corrupt-crt" -> Files.writeString(pki.properties().caCertificate(), "original corrupt crt");
            case "mismatched-crt" -> {
                PkiTestFixture unrelated = PkiTestFixture.create(directory.resolve("unrelated"));
                Files.copy(unrelated.properties().caCertificate(), pki.properties().caCertificate(), StandardCopyOption.REPLACE_EXISTING);
            }
            case "wrong-password" -> pki.properties().setDefaultKeystorePassword("incorrect-test-password");
            case "missing-revocations" -> Files.delete(pki.properties().revocations());
            case "corrupt-revocations" -> Files.writeString(pki.properties().revocations(), "not-json");
            case "invalid-revocation-serial" -> Files.writeString(pki.properties().revocations(), "[\"not-a-hex-serial\"]");
            default -> { }
        }
        Map<String, byte[]> before = snapshot(store);

        assertRecoveryRequired(pki);

        assertUnchanged(before, store);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user_1001.p12", "user_1001.crt", ".user_1001.p12.interrupted.tmp",
            ".ca.p12.interrupted.tmp", ".ca.crt.interrupted.tmp",
            "ca.key.tmp", "ca.p12.tmp", "ca.crt.tmp", "revocations", "invalid-revocations"
    })
    void orphanedOrTemporaryMaterialBlocksFreshInitialization(String artifact) throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        if (artifact.equals("revocations") || artifact.equals("invalid-revocations")) {
            Files.createDirectories(pki.properties().revocations().getParent());
            Files.writeString(pki.properties().revocations(), artifact.equals("revocations") ? "[\"ABC123\"]" : "invalid-json");
        } else {
            Path target = artifact.contains("user_")
                    ? pki.properties().userKeystores().resolve(artifact)
                    : pki.properties().caKeystore().resolveSibling(artifact);
            Files.createDirectories(target.getParent());
            Files.writeString(target, "original artifact");
        }
        Map<String, byte[]> before = snapshot(directory);

        assertRecoveryRequired(pki);

        assertUnchanged(before, directory);
        assertFalse(Files.exists(pki.properties().caKeystore()));
        assertFalse(Files.exists(pki.properties().caCertificate()));
    }

    @Test
    void signingHistoryBlocksInitializationEvenWhenAllKeyFilesWereLost() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        when(pki.history().hasAnySigningHistory()).thenReturn(true);

        assertRecoveryRequired(pki);

        assertFalse(Files.exists(pki.properties().caKeystore()));
        assertFalse(Files.exists(pki.properties().caCertificate()));
        assertFalse(Files.exists(pki.properties().revocations()));
    }

    @Test
    void unavailableHistoryDoesNotPermitBlindCreation() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        when(pki.history().hasAnySigningHistory()).thenThrow(new DataAccessResourceFailureException("test database unavailable"));

        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::status);
        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::initialize);

        assertFalse(Files.exists(pki.properties().caKeystore()));
        assertFalse(Files.exists(pki.properties().caCertificate()));
    }

    @Test
    void anExistingEmptyRevocationFileIsPreservedByteForByte() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        Files.createDirectories(pki.properties().revocations().getParent());
        String original = "[  ]\n";
        Files.writeString(pki.properties().revocations(), original);

        assertEquals(State.NOT_INITIALIZED, pki.caService().status().state());
        assertEquals(State.READY, pki.caService().initialize().state());

        assertEquals(original, Files.readString(pki.properties().revocations()));
    }

    @Test
    void anUnusableStorageParentFailsClearlyWithoutOverwritingIt() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "existing file prevents directory access");
        PkiTestFixture pki = PkiTestFixture.withoutCa(blocked);

        assertError(ErrorCode.PKI_STORAGE_ERROR, pki.caService()::initialize);

        assertEquals("existing file prevents directory access", Files.readString(blocked));
        assertEquals(List.of(blocked), entries(directory));
    }

    @Test
    void twoServiceInstancesInitializeExactlyOneCa() throws Exception {
        PkiTestFixture pki = PkiTestFixture.withoutCa(directory);
        PkiCaService other = new PkiCaService(pki.properties(), pki.history());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> initializeAfter(start, pki.caService()));
            var second = executor.submit(() -> initializeAfter(start, other));
            start.countDown();

            List<String> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("READY"::equals).count());
            assertEquals(1, outcomes.stream().filter("PKI_CA_ALREADY_EXISTS"::equals).count());
        }
        assertEquals(State.READY, pki.caService().status().state());
        Map<String, byte[]> before = snapshot(directory);
        assertError(ErrorCode.PKI_CA_ALREADY_EXISTS, other::initialize);
        assertUnchanged(before, directory);
    }

    @Test
    void aNewServiceAndRestoredBackupUseTheSameCaFingerprintAndUserKeys() throws Exception {
        Path originalRoot = directory.resolve("original");
        PkiTestFixture original = PkiTestFixture.withoutCa(originalRoot);
        PkiCaStatusResponse created = original.caService().initialize();
        original.signer().signBytes(pdf(), "1001", "Nguyen Van A", "Ký trước khi sao lưu");
        byte[] userKey = Files.readAllBytes(original.properties().userKeystores().resolve("user_1001.p12"));

        PkiCaService restarted = new PkiCaService(original.properties(), original.history());
        assertEquals(created.fingerprintSha256(), restarted.status().fingerprintSha256());

        Path restoredRoot = directory.resolve("restored");
        PkiTestFixture restored = PkiTestFixture.withoutCa(restoredRoot);
        copyStore(originalRoot, restoredRoot);
        PkiCaStatusResponse restoredStatus = restored.caService().status();
        assertEquals(State.READY, restoredStatus.state());
        assertEquals(created.fingerprintSha256(), restoredStatus.fingerprintSha256());
        var signed = restored.signer().signBytes(pdf(), "1001", "Nguyen Van A", "Ký sau khi khôi phục");
        assertEquals("VALID", restored.verifier().verify(signed.signedPdfBytes()).getOverallStatus());
        assertArrayEquals(userKey, Files.readAllBytes(restored.properties().userKeystores().resolve("user_1001.p12")));
        assertError(ErrorCode.PKI_CA_ALREADY_EXISTS, restored.caService()::initialize);
    }

    private String initializeAfter(CountDownLatch start, PkiCaService service) throws InterruptedException {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            return service.initialize().state().name();
        } catch (BusinessException error) {
            return error.getErrorCode().getCode();
        }
    }

    private void assertRecoveryRequired(PkiTestFixture pki) {
        PkiCaStatusResponse status = pki.caService().status();
        assertEquals(State.RECOVERY_REQUIRED, status.state());
        assertFalse(status.canInitialize());
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::requireValidCa);
        assertError(ErrorCode.PKI_CA_RECOVERY_REQUIRED, pki.caService()::initialize);
    }

    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        BusinessException error = assertThrows(BusinessException.class, action);
        assertEquals(expected, error.getErrorCode());
    }

    /** Lock files may be created by mutations; all other artifact bytes must remain unchanged. */
    private Map<String, byte[]> snapshot(Path root) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        if (!Files.exists(root)) return result;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (!path.getFileName().toString().endsWith(".lock")) {
                    result.put(root.relativize(path).toString(), Files.readAllBytes(path));
                }
            }
        }
        return result;
    }

    private void assertUnchanged(Map<String, byte[]> before, Path root) throws IOException {
        Map<String, byte[]> after = snapshot(root);
        assertEquals(before.keySet(), after.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), "Artifact changed: " + path));
    }

    private List<Path> entries(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> !path.equals(root)).sorted().toList();
        }
    }

    private void copyStore(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target.resolve(source.relativize(path)));
                } else if (!path.getFileName().toString().endsWith(".lock")) {
                    Files.copy(path, target.resolve(source.relativize(path)));
                }
            }
        }
    }

    private byte[] pdf() throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(output);
            return output.toByteArray();
        }
    }
}

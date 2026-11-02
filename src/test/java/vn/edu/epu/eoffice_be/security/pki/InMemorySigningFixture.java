package vn.edu.epu.eoffice_be.security.pki;

import org.junit.jupiter.api.Assertions;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse;
import vn.edu.epu.eoffice_be.service.PkiCaService;
import vn.edu.epu.eoffice_be.service.PkiUserKeystoreService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

/** Real PDF signer/verifier, synthetic unwrapped keys. No PKCS12 or encryption service exists here. */
public final class InMemorySigningFixture {
    private final PkiProperties properties = new PkiProperties();
    private final PkiCaService ca;
    private final PkiUserKeystoreService users;
    private final Map<String, Crypto.P12Bundle> bundles = new ConcurrentHashMap<>();
    private final AtomicInteger provisions = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger publicReads = new AtomicInteger();
    private final AtomicInteger unexpectedCalls = new AtomicInteger();
    private final Crypto.P12Bundle root = PkiTestSamples.root();

    private InMemorySigningFixture(Path directory) throws Exception {
        properties.setRevokedPath(directory.resolve("ca/revoked.json").toString());
        Files.createDirectories(properties.revocations().getParent());
        Files.writeString(properties.revocations(), "[]");
        ca = mock(PkiCaService.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "activeCertificate":
                    publicReads.incrementAndGet();
                    return Optional.of(root.getEndEntityCertificate());
                case "status":
                    publicReads.incrementAndGet();
                    var cert = root.getEndEntityCertificate();
                    return new PkiCaStatusResponse(PkiCaStatusResponse.State.READY, false,
                            cert.getSubjectX500Principal().getName(), PkiCaService.certificateFingerprint(cert),
                            cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant());
                default:
                    unexpectedCalls.incrementAndGet();
                    throw new AssertionError("Unexpected CA/private-keystore access: " + invocation.getMethod().getName());
            }
        });
        users = mock(PkiUserKeystoreService.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "ensure":
                    provisions.incrementAndGet();
                    String id = invocation.getArgument(0);
                    bundles.computeIfAbsent(id, ignored -> PkiTestSamples.user(root, id,
                            invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
                    return null;
                case "require":
                    reads.incrementAndGet();
                    var bundle = bundles.get((String) invocation.getArgument(0));
                    if (bundle == null) throw new AssertionError("Synthetic key was not prepared");
                    return new Crypto.P12Bundle(bundle.privateKey(), bundle.chain().clone());
                default:
                    unexpectedCalls.incrementAndGet();
                    throw new AssertionError("Unexpected PKCS12 dependency: " + invocation.getMethod().getName());
            }
        });
    }

    public static InMemorySigningFixture create(Path directory) throws Exception {
        return new InMemorySigningFixture(directory);
    }
    public PkiProperties properties() { return properties; }
    public PkiCaService caService() { return ca; }
    public PdfSigner signer() {
        var signer = new PdfSigner(users);
        Path directory = properties.revocations().getParent();
        ReflectionTestUtils.setField(signer, "signaturesDir", directory.resolve("images").toString());
        ReflectionTestUtils.setField(signer, "stampsDir", directory.resolve("stamps").toString());
        return signer;
    }
    public PdfSignatureVerifier verifier() { return new PdfSignatureVerifier(ca, properties); }
    public void assertOnlyInMemoryKeysUsed() {
        Assertions.assertEquals(0, unexpectedCalls.get(), "No private CA/PKCS12 path may be used");
        if (reads.get() > 0) Assertions.assertTrue(provisions.get() > 0);
        Assertions.assertTrue(reads.get() > 0 || publicReads.get() > 0,
                "Exercise either real signing with the memory provider or public verification");
    }
}

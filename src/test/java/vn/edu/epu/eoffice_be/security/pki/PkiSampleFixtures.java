package vn.edu.epu.eoffice_be.security.pki;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import vn.edu.epu.eoffice_be.service.PkiCaService;
import static org.mockito.Mockito.*;

/** Opt-in templates: the original PkiTestFixture is unchanged for all other tests. */
public final class PkiSampleFixtures {
    private PkiSampleFixtures() { }

    public static PkiTestFixture create(Path directory) throws Exception {
        return create(directory, PkiTestSamples.root(), false);
    }

    public static PkiTestFixture cachedCa(Path directory) throws Exception {
        return create(directory, PkiTestSamples.root(), true);
    }

    public static PkiTestFixture otherCa(Path directory) throws Exception {
        Instant now = Instant.now();
        return create(directory, PkiTestSamples.root("other", now.minusSeconds(86400), now.plusSeconds(86400000)), false);
    }

    public static PkiTestFixture create(Path directory, Instant before, Instant after) throws Exception {
        return create(directory, PkiTestSamples.root("main", before, after), false);
    }

    private static PkiTestFixture create(Path directory, Crypto.P12Bundle root, boolean cached) throws Exception {
        var fixture = PkiTestFixture.withoutCa(directory);
        fixture.repository().saveAndFlush(PkiTestSamples.caRecord(root, fixture.properties(), fixture.encryption()));
        Files.createDirectories(fixture.properties().revocations().getParent());
        Files.writeString(fixture.properties().revocations(), "[]");
        if (!cached) return fixture;
        PkiCaService service = spy(fixture.caService());
        // Only the CA dependency is replaced; the user service and its validation remain real.
        doAnswer(invocation -> new Crypto.P12Bundle(root.privateKey(), root.chain().clone()))
                .when(service).requireValidCa();
        return new PkiTestFixture(fixture.properties(), service, fixture.history(), fixture.repository(),
                fixture.encryption(), fixture.encryptionProperties(), fixture.userRepository(), fixture.users());
    }

    public static PkiTestFixture existingUser(Path directory) throws Exception {
        var fixture = cachedCa(directory);
        var bundle = PkiTestSamples.user(PkiTestSamples.root(), "1001", "A", null, null);
        fixture.userRepository().saveAndFlush(PkiTestSamples.userRecord(bundle, 1001L, 1L,
                fixture.properties(), fixture.encryption()));
        return fixture;
    }
}

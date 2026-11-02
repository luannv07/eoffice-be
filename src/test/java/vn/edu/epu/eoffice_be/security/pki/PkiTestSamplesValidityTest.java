package vn.edu.epu.eoffice_be.security.pki;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PkiTestSamplesValidityTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    static Stream<Arguments> validityPeriods() {
        return Stream.of(
                Arguments.of("expired", -30, -1),
                Arguments.of("active", -1, 30),
                Arguments.of("future", 1, 30),
                Arguments.of("beyond-default-user-expiry", 400, 500),
                Arguments.of("starts-now", 0, 30),
                Arguments.of("equal-boundaries", -2, -2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validityPeriods")
    void userValidityIsOrderedAndContainedInCaValidity(String scenario, int fromDays, int toDays) throws Exception {
        // Reuse cached RSA pairs; this regression needs certificates, not PKCS12 encode/decode.
        var root = PkiTestSamples.root("validity-regression", NOW.plus(fromDays, ChronoUnit.DAYS),
                NOW.plus(toDays, ChronoUnit.DAYS));
        var ca = root.getEndEntityCertificate();
        var leaf = PkiTestSamples.user(root, "1001", "Validity sample", null, null).getEndEntityCertificate();

        assertFalse(leaf.getNotBefore().after(leaf.getNotAfter()), "notBefore must not exceed notAfter");
        assertFalse(leaf.getNotBefore().before(ca.getNotBefore()), "notBefore must stay within the CA period");
        assertFalse(leaf.getNotBefore().after(ca.getNotAfter()), "notBefore must not exceed CA expiry");
        assertFalse(leaf.getNotAfter().before(ca.getNotBefore()), "notAfter must not precede CA validity");
        assertFalse(leaf.getNotAfter().after(ca.getNotAfter()), "notAfter must not exceed CA expiry");
        leaf.verify(ca.getPublicKey());

        // Fixing the date ordering must not accidentally make expired/future samples valid now.
        Date at = Date.from(NOW);
        if (ca.getNotAfter().before(at)) {
            assertThrows(CertificateExpiredException.class, () -> leaf.checkValidity(at));
        } else if (ca.getNotBefore().after(at)) {
            assertThrows(CertificateNotYetValidException.class, () -> leaf.checkValidity(at));
        } else {
            assertDoesNotThrow(() -> leaf.checkValidity(at));
        }
    }
}

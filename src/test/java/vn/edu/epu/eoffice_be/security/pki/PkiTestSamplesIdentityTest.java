package vn.edu.epu.eoffice_be.security.pki;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PkiTestSamplesIdentityTest {
    @Test
    void nullIdIsRejectedBeforeReadingCaOrBuildingSampleMaterial() {
        // No CA/key generation is necessary to reject this invalid argument.
        var error = assertThrows(IllegalArgumentException.class,
                () -> PkiTestSamples.user(null, null, "Sample user", null, null));
        assertEquals("ID người dùng mẫu không được null", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void validIdStillAppearsInCertificateAndOptionalFieldsMayBeNull() throws Exception {
        var root = PkiTestSamples.root();
        var certificate = PkiTestSamples.user(root, "1001", null, null, null).getEndEntityCertificate();
        var subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        assertEquals("1001", subject.getRDNs(BCStyle.UID)[0].getFirst().getValue().toString());
        assertEquals("Can bo 1001", subject.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());
        certificate.verify(root.getEndEntityCertificate().getPublicKey());
    }
}

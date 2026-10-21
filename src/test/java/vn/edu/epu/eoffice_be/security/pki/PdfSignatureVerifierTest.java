package vn.edu.epu.eoffice_be.security.pki;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.SignatureItemResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class PdfSignatureVerifierTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setup() {
        Crypto.registerBouncyCastle();
    }

    private byte[] createSamplePdfBytes() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 720);
                cs.showText("VAN BAN THU NGHIEM KIEM DINH CHU KY SO");
                cs.endText();
            }

            document.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Kiểm định tài liệu không có chữ ký số -> overallStatus = INVALID, totalSignatures = 0")
    void verify_noSignatures_shouldReturnInvalid() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes();
        PdfSignatureVerifier verifier = PkiTestFixture.withoutCa(tempDir).verifier();

        DocumentSignatureVerifyResponse response = verifier.verify(pdfBytes);

        assertNotNull(response);
        assertEquals("INVALID", response.getOverallStatus());
        assertEquals(0, response.getTotalSignatures());
        assertTrue(response.getWarningsOrErrors().get(0).contains("không chứa bất kỳ chữ ký"));
    }

    @Test
    @DisplayName("Kiểm định thành công văn bản có 2 chữ ký tuần tự đạt 100% 7 tiêu chí bảo mật")
    void verify_sequentialSignatures_shouldPassAllCriteria() throws Exception {
        byte[] basePdf = createSamplePdfBytes();
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        PdfSigner signer = pki.signer();

        // 1. Cán bộ 1001 ký nháy với Chức vụ và Đơn vị
        PdfSigner.SignResult res1 = signer.signBytes(basePdf, "1001", "Nguyen Van A - Chuyen vien CNTT", "Ký nháy chuyên viên");
        byte[] signed1 = res1.signedPdfBytes();
        assertNotNull(signed1);

        // 2. Cán bộ 1002 ký duyệt
        PdfSigner.SignResult res2 = signer.signBytes(signed1, "1002", "Tran Thi B - Truong phong Dao tao", "Ký duyệt trưởng phòng");
        byte[] signed2 = res2.signedPdfBytes();
        assertNotNull(signed2);

        // 3. Khởi tạo Verifier với cùng Root CA
        PdfSignatureVerifier verifier = pki.verifier();

        DocumentSignatureVerifyResponse response = verifier.verify(signed2);

        assertNotNull(response);
        assertEquals("VALID", response.getOverallStatus());
        assertEquals(2, response.getTotalSignatures());
        assertTrue(response.getWarningsOrErrors().isEmpty());

        // Chữ ký 1: Nguyen Van A
        SignatureItemResponse sig1 = response.getSignatures().get(0);
        assertEquals(1, sig1.getIndex());
        assertEquals("sig_user_1001", sig1.getFieldName());
        assertEquals("1001", sig1.getUserId());
        assertEquals("Nguyen Van A", sig1.getSignerName());
        assertEquals("Chuyen vien CNTT", sig1.getSignerTitle());
        assertTrue(sig1.isIntegrityPass());
        assertTrue(sig1.isChainPass());
        assertTrue(sig1.isValidityPass());
        assertTrue(sig1.isKeyUsagePass());
        assertTrue(sig1.isRevocationPass());
        assertTrue(sig1.isCoveragePass());
        assertTrue(sig1.isIdentityPass());
        assertTrue(sig1.isFullyValid());

        // Chữ ký 2: Tran Thi B
        SignatureItemResponse sig2 = response.getSignatures().get(1);
        assertEquals(2, sig2.getIndex());
        assertEquals("sig_user_1002", sig2.getFieldName());
        assertEquals("1002", sig2.getUserId());
        assertEquals("Tran Thi B", sig2.getSignerName());
        assertEquals("Truong phong Dao tao", sig2.getSignerTitle());
        assertTrue(sig2.isIntegrityPass());
        assertTrue(sig2.isChainPass());
        assertTrue(sig2.isValidityPass());
        assertTrue(sig2.isKeyUsagePass());
        assertTrue(sig2.isRevocationPass());
        assertTrue(sig2.isCoveragePass());
        assertTrue(sig2.isIdentityPass());
        assertTrue(sig2.isFullyValid());
    }

    @Test
    @DisplayName("Kiểm định phát hiện chứng thư đã bị thu hồi (Revocation CRL)")
    void verify_revokedCertificate_shouldFailRevocationCriterion() throws Exception {
        byte[] basePdf = createSamplePdfBytes();
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        Path revokedPath = pki.properties().revocations();
        PdfSigner signer = pki.signer();

        PdfSigner.SignResult res = signer.signBytes(basePdf, "1003", "Le Van C - Can bo khao thi", "Ký nháy");
        byte[] signed = res.signedPdfBytes();

        // Trích xuất số serial từ chữ ký vừa ký
        PdfSignatureVerifier preVerifier = pki.verifier();
        DocumentSignatureVerifyResponse preRes = preVerifier.verify(signed);
        String serialHex = preRes.getSignatures().get(0).getSerialHex();

        // Ghi serialHex vào file revoked.json
        Files.writeString(revokedPath, "[\"" + serialHex + "\"]");

        // Kiểm tra lại qua verifier
        PdfSignatureVerifier verifier = pki.verifier();
        DocumentSignatureVerifyResponse verifyRes = verifier.verify(signed);

        assertEquals("INVALID", verifyRes.getOverallStatus());
        assertFalse(verifyRes.getSignatures().get(0).isRevocationPass());
        assertTrue(verifyRes.getSignatures().get(0).getRevocationDetails().contains("REVOKED"));
    }

    @Test
    void revocationWriterWaitsForCaReadLockInTheSameJvm() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        Path revocations = pki.properties().revocations();
        CountDownLatch writerStarted = new CountDownLatch(1);
        AtomicReference<Future<?>> writer = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            PkiFileSupport.withJvmLock(revocations, () -> {
                try (FileChannel channel = FileChannel.open(revocations, StandardOpenOption.READ);
                     FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
                    writer.set(executor.submit(() -> {
                        writerStarted.countDown();
                        pki.verifier().revokeSerial("abcdef");
                    }));
                    try {
                        assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    // It must wait for the JVM mutex, rather than throwing OverlappingFileLockException.
                    assertThrows(TimeoutException.class, () -> writer.get().get(150, TimeUnit.MILLISECONDS));
                }
                return null;
            });
            writer.get().get(5, TimeUnit.SECONDS);
        }
        assertTrue(Files.readString(revocations).contains("abcdef"));
        assertEquals(vn.edu.epu.eoffice_be.dto.response.PkiCaStatusResponse.State.READY, pki.caService().status().state());
    }

    @Test
    void verificationWaitsForCrlWriteAndObservesTheRevokedSerial() throws Exception {
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        var signed = pki.signer().signBytes(createSamplePdfBytes(), "1001", "Nguyen Van A", "Ký");
        Path revocations = pki.properties().revocations();
        CountDownLatch readerStarted = new CountDownLatch(1);
        AtomicReference<Future<DocumentSignatureVerifyResponse>> reader = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            PkiFileSupport.withJvmLock(revocations, () -> {
                try (FileChannel channel = FileChannel.open(revocations, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    channel.truncate(0);
                    channel.force(true);
                    reader.set(executor.submit(() -> {
                        readerStarted.countDown();
                        return pki.verifier().verify(signed.signedPdfBytes());
                    }));
                    try {
                        assertTrue(readerStarted.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    // An unlocked read of the empty intermediate file would incorrectly accept this certificate.
                    assertThrows(TimeoutException.class, () -> reader.get().get(150, TimeUnit.MILLISECONDS));
                    ByteBuffer revoked = ByteBuffer.wrap(("[\"" + signed.serialHex() + "\"]").getBytes(StandardCharsets.UTF_8));
                    while (revoked.hasRemaining()) channel.write(revoked);
                    channel.force(true);
                }
                return null;
            });
            DocumentSignatureVerifyResponse result = reader.get().get(5, TimeUnit.SECONDS);
            assertEquals("INVALID", result.getOverallStatus());
            assertFalse(result.getSignatures().get(0).isRevocationPass());
        }
    }
}

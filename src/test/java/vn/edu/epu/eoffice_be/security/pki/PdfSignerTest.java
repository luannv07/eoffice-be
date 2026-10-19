package vn.edu.epu.eoffice_be.security.pki;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PdfSignerTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setup() {
        Crypto.registerBouncyCastle();
    }

    private void createSamplePdf(Path targetFile) throws Exception {
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 720);
                cs.showText("TRUONG DAI HOC DIEN LUC - EPU");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.showText("VAN BAN DU THAO DE THU NGHIEM KY NHAY SO");
                cs.endText();
            }

            document.save(targetFile.toFile());
        }
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
                cs.showText("VAN BAN DU THAO KY NHAY IN-MEMORY");
                cs.endText();
            }

            document.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void testSequentialMultiSignature() throws Exception {
        Path basePdf = tempDir.resolve("doc_base.pdf");
        createSamplePdf(basePdf);

        Path signed1 = tempDir.resolve("doc_signed_1.pdf");
        Path signed2 = tempDir.resolve("doc_signed_2.pdf");

        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        PdfSigner signer = pki.signer();
        signer.ensureUserCertificate("1001", "Nguyen Van A");
        signer.ensureUserCertificate("1002", "Tran Thi B");

        // 1. Cán bộ 1001 ký nháy lần 1
        PdfSigner.SignResult res1 = signer.sign(basePdf, signed1, "1001", "Ký nháy chuyên viên");
        assertNotNull(res1);
        assertEquals("1001", res1.userId());
        assertEquals("sig_user_1001", res1.fieldName());
        assertNotNull(res1.outPdfSha256());

        try (PDDocument doc1 = Loader.loadPDF(signed1.toFile())) {
            List<PDSignature> sigs = doc1.getSignatureDictionaries();
            assertEquals(1, sigs.size(), "Phải có đúng 1 chữ ký của cán bộ 1");
            assertEquals("Nguyen Van A", sigs.get(0).getName());
            assertEquals("Ký nháy chuyên viên", sigs.get(0).getReason());
            assertEquals("Adobe.PPKLite", sigs.get(0).getFilter());
            assertEquals("adbe.pkcs7.detached", sigs.get(0).getSubFilter());
        }

        // 2. Cán bộ 1002 ký nháy lần 2 (nối tiếp từ output của cán bộ 1)
        PdfSigner.SignResult res2 = signer.sign(signed1, signed2, "1002", "Trưởng phòng thẩm định");
        assertNotNull(res2);
        assertEquals("1002", res2.userId());
        assertEquals("sig_user_1002", res2.fieldName());

        // Kiểm tra file sau khi người 2 ký: CẢ 2 CHỮ KÝ ĐỀU PHẢI TỒN TẠI VÀ HỢP LỆ NGUYÊN VẸN
        try (PDDocument doc2 = Loader.loadPDF(signed2.toFile())) {
            List<PDSignature> sigs = doc2.getSignatureDictionaries();
            assertEquals(2, sigs.size(), "Phải có đúng 2 chữ ký");

            assertEquals("Nguyen Van A", sigs.get(0).getName());
            assertEquals("Ký nháy chuyên viên", sigs.get(0).getReason());

            assertEquals("Tran Thi B", sigs.get(1).getName());
            assertEquals("Trưởng phòng thẩm định", sigs.get(1).getReason());

            List<PDSignatureField> fields = doc2.getSignatureFields();
            assertEquals(2, fields.size());
            assertTrue(fields.stream().anyMatch(f -> "sig_user_1001".equals(f.getPartialName())));
            assertTrue(fields.stream().anyMatch(f -> "sig_user_1002".equals(f.getPartialName())));

            // Xác minh tính hợp lệ mật mã của chữ ký người 1 và người 2 trên file cuối cùng
            verifyPdfSignature(signed2, sigs.get(0));
            verifyPdfSignature(signed2, sigs.get(1));
        }

        // 3. Kiểm tra chống ký trùng lặp: người 1001 không được ký lại trên cùng tài liệu
        Path signedDuplicate = tempDir.resolve("doc_dup.pdf");
        DuplicateSignatureException ex = assertThrows(DuplicateSignatureException.class, () -> {
            signer.sign(signed2, signedDuplicate, "1001", "Cố tình ký lại");
        });
        assertTrue(ex.getMessage().contains("User đã ký: 1001"));
    }

    @Test
    void testSignBytesOverload() throws Exception {
        byte[] originalBytes = createSamplePdfBytes();
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        PdfSigner signer = pki.signer();

        PdfSigner.SignResult result = signer.signBytes(originalBytes, "1001", "Nguyen Van A", "Ký nháy byte array");
        assertNotNull(result);
        assertNotNull(result.signedPdfBytes());
        assertTrue(result.signedPdfBytes().length > originalBytes.length);

        // Đọc lại mảng byte đã ký và kiểm tra
        try (PDDocument doc = Loader.loadPDF(result.signedPdfBytes())) {
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            assertEquals(1, sigs.size());
            assertEquals("Nguyen Van A", sigs.get(0).getName());
        }
    }

    @Test
    void testSignAndCertifyBytes_sequentialApprovalAndCertification_shouldPass7Criteria() throws Exception {
        byte[] originalBytes = createSamplePdfBytes();
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        PdfSigner signer = pki.signer();

        // 1. Chuyên viên 1001 ký nháy
        PdfSigner.SignResult approvalResult = signer.signBytes(
                originalBytes,
                "1001",
                "Nguyen Van A",
                "Chuyên viên",
                "Phong Dao tao",
                "Ký nháy chuyên viên"
        );
        assertNotNull(approvalResult);
        assertEquals("sig_user_1001", approvalResult.fieldName());

        // 2. Lãnh đạo 1002 ký phát hành chính thức kèm DocMDP và toạ độ SignaturePosition
        SignaturePosition pos = new SignaturePosition(1, 350f, 150f, 180f, 60f);
        PdfSigner.SignResult certifyResult = signer.signAndCertifyBytes(
                approvalResult.signedPdfBytes(),
                "1002",
                "Tran Thi B",
                "Hieu truong",
                "Ban Giam hieu",
                "Ban hanh quyet dinh",
                pos
        );
        assertNotNull(certifyResult);
        assertEquals("sig_publish_1002", certifyResult.fieldName());

        // 3. Kiểm tra bằng PdfSignatureVerifier (7 tiêu chí)
        PdfSignatureVerifier verifier = pki.verifier();
        vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse verifyResponse =
                verifier.verify(certifyResult.signedPdfBytes());

        assertNotNull(verifyResponse);
        assertEquals(2, verifyResponse.getTotalSignatures());
        assertEquals("VALID", verifyResponse.getOverallStatus(), "Tài liệu sau khi ký phát hành DocMDP phải đạt 100% VALID");

        // Kiểm tra chữ ký 1 (ký nháy)
        var sig1 = verifyResponse.getSignatures().get(0);
        assertEquals("sig_user_1001", sig1.getFieldName());
        assertTrue(sig1.isFullyValid());
        assertTrue(sig1.isIdentityPass());

        // Kiểm tra chữ ký 2 (ký phát hành)
        var sig2 = verifyResponse.getSignatures().get(1);
        assertEquals("sig_publish_1002", sig2.getFieldName());
        assertTrue(sig2.isFullyValid());
        assertTrue(sig2.isIdentityPass());
        assertTrue(sig2.isCoveragePass());
        assertTrue(sig2.getCoverageDetails().contains("FULL_FILE"));
    }

    @Test
    void testSignVisibleBytes_LeaderApprove_WithoutDocMdpLock() throws Exception {
        byte[] rawPdf = createSamplePdfBytes();
        PkiTestFixture pki = PkiTestFixture.create(tempDir);
        PdfSigner signer = pki.signer();

        // 1. Hiệu trưởng ký số hiển thị với ảnh con dấu tại tọa độ mặc định (trang cuối)
        PdfSigner.SignResult leaderResult = signer.signVisibleBytes(
                rawPdf,
                "1002",
                "GS. TS. Nguyen Van A",
                "Hiệu trưởng",
                "Ban Giám hiệu",
                "Hiệu trưởng phê duyệt văn bản",
                null // null = lấy tọa độ mặc định trang cuối
        );

        assertNotNull(leaderResult);
        assertEquals("sig_approve_1002", leaderResult.fieldName());
        assertNotNull(leaderResult.signedPdfBytes());

        // 2. Tiếp tục ký nháy của Văn thư mà không bị lỗi (vì chưa bị DocMDP P=1 khóa chết)
        PdfSigner.SignResult clerkResult = signer.signBytes(
                leaderResult.signedPdfBytes(),
                "3003",
                "Tran Thi B - Cán bộ văn thư",
                "Văn thư ký nháy khi cấp số"
        );
        assertNotNull(clerkResult);
        assertNotNull(clerkResult.signedPdfBytes());

        // 3. Kiểm định bằng PdfSignatureVerifier: cả 2 chữ ký đều hợp lệ
        PdfSignatureVerifier verifier = pki.verifier();
        var verifyResponse = verifier.verify(clerkResult.signedPdfBytes());

        assertNotNull(verifyResponse);
        assertEquals(2, verifyResponse.getTotalSignatures());
        assertEquals("VALID", verifyResponse.getOverallStatus());
    }

    private void verifyPdfSignature(Path pdfPath, PDSignature sig) throws Exception {
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        int[] byteRange = sig.getByteRange();

        int length = byteRange[1] + byteRange[3];
        byte[] signedContent = new byte[length];
        System.arraycopy(pdfBytes, byteRange[0], signedContent, 0, byteRange[1]);
        System.arraycopy(pdfBytes, byteRange[2], signedContent, byteRange[1], byteRange[3]);

        byte[] signatureBytes = sig.getContents();
        org.bouncycastle.asn1.ASN1InputStream asn1In = new org.bouncycastle.asn1.ASN1InputStream(signatureBytes);
        org.bouncycastle.asn1.ASN1Primitive primitive = asn1In.readObject();
        org.bouncycastle.asn1.cms.ContentInfo contentInfo = org.bouncycastle.asn1.cms.ContentInfo.getInstance(primitive);
        CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), contentInfo);

        Store<X509CertificateHolder> certStore = cms.getCertificates();
        for (SignerInformation signerInfo : cms.getSignerInfos()) {
            Collection<X509CertificateHolder> matches = certStore.getMatches(signerInfo.getSID());
            assertFalse(matches.isEmpty(), "Phải tìm thấy chứng thư của signer trong CMS");
            X509CertificateHolder holder = matches.iterator().next();

            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(holder.getEncoded()));

            boolean verified = signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(cert));
            assertTrue(verified, "Chữ ký mật mã của " + cert.getSubjectX500Principal() + " phải hợp lệ 100%");
        }
    }
}

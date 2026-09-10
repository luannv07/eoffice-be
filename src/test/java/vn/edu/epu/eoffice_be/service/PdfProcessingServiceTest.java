package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.service.impl.PdfProcessingServiceImpl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfProcessingServiceTest {

    private PdfProcessingService pdfProcessingService;

    @BeforeEach
    void setUp() {
        pdfProcessingService = new PdfProcessingServiceImpl();
    }

    @Test
    @DisplayName("Tạo PDF mẫu thành công")
    void createSamplePdf_shouldReturnValidBytes() {
        byte[] pdfBytes = pdfProcessingService.createSamplePdf(
                "Ke hoach Cong tac 2026",
                "Noi dung ke hoach to chuc nghien cuu khoa hoc EPU"
        );

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    @DisplayName("UC13: Nhúng Digital Stamp ký nháy vào PDF thành công")
    void applyInitialSignStamp_shouldAddStampSuccessfully() {
        byte[] originalPdf = pdfProcessingService.createSamplePdf("Du thao", "Noi dung du thao van ban");

        StampInfo stampInfo = StampInfo.builder()
                .signerName("Nguyen Van A")
                .positionTitle("Truong Bo Mon")
                .signDate("2026-09-10 16:00:00")
                .note("Tham dinh noi dung")
                .build();

        byte[] stampedPdf = pdfProcessingService.applyInitialSignStamp(originalPdf, stampInfo);

        assertNotNull(stampedPdf);
        assertTrue(stampedPdf.length > originalPdf.length);
    }

    @Test
    @DisplayName("UC14: Ký số PKI X.509 PAdES và khóa niêm phong PDF thành công")
    void signAndCertifyPdf_shouldSignSuccessfully() {
        byte[] originalPdf = pdfProcessingService.createSamplePdf("Quyet dinh", "Quyet dinh ban hanh");

        byte[] certifiedPdf = pdfProcessingService.signAndCertifyPdf(
                originalPdf,
                "Hieu Truong - Dai hoc Dien Luc",
                "Ky phat hanh van ban chinh thuc 125/QD-DHEPU"
        );

        assertNotNull(certifiedPdf);
        assertTrue(certifiedPdf.length > originalPdf.length);
    }
}

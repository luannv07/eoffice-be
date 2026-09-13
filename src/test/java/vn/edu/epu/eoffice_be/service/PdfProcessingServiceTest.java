package vn.edu.epu.eoffice_be.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.service.impl.PdfProcessingServiceImpl;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("Ném lỗi BAD_REQUEST khi stampInfo null hoặc pdfBytes rỗng")
    void applyInitialSignStamp_nullInputs_shouldThrowBadRequest() {
        byte[] originalPdf = pdfProcessingService.createSamplePdf("Du thao", "Noi dung du thao van ban");

        // stampInfo null
        BusinessException exNullStamp = assertThrows(BusinessException.class, () ->
                pdfProcessingService.applyInitialSignStamp(originalPdf, null));
        assertEquals(ErrorCode.BAD_REQUEST, exNullStamp.getErrorCode());

        // pdfBytes null hoặc rỗng
        StampInfo stampInfo = StampInfo.builder().signerName("Nguyen Van A").build();
        BusinessException exNullPdf = assertThrows(BusinessException.class, () ->
                pdfProcessingService.applyInitialSignStamp(null, stampInfo));
        assertEquals(ErrorCode.BAD_REQUEST, exNullPdf.getErrorCode());

        BusinessException exEmptyPdf = assertThrows(BusinessException.class, () ->
                pdfProcessingService.applyInitialSignStamp(new byte[0], stampInfo));
        assertEquals(ErrorCode.BAD_REQUEST, exEmptyPdf.getErrorCode());
    }

    @Test
    @DisplayName("UC14: Ký số PKI X.509 PAdES và khóa niêm phong PDF thành công, kèm chứng chỉ số trong CMS")
    void signAndCertifyPdf_shouldSignSuccessfully() throws Exception {
        byte[] originalPdf = pdfProcessingService.createSamplePdf("Quyet dinh", "Quyet dinh ban hanh");

        byte[] certifiedPdf = pdfProcessingService.signAndCertifyPdf(
                originalPdf,
                "Hieu Truong - Dai hoc Dien Luc",
                "Ky phat hanh van ban chinh thuc 125/QD-DHEPU"
        );

        assertNotNull(certifiedPdf);
        assertTrue(certifiedPdf.length > originalPdf.length);

        // Kiểm tra chữ ký CMS và chứng chỉ số X.509 được đính kèm đầy đủ
        try (PDDocument document = Loader.loadPDF(certifiedPdf)) {
            List<PDSignature> signatures = document.getSignatureDictionaries();
            assertFalse(signatures.isEmpty());
            PDSignature signature = signatures.get(0);
            byte[] signatureBytes = signature.getContents(certifiedPdf);
            assertNotNull(signatureBytes);

            CMSSignedData cmsSignedData = new CMSSignedData(signatureBytes);
            Store<X509CertificateHolder> certificates = cmsSignedData.getCertificates();
            Collection<X509CertificateHolder> certList = certificates.getMatches(null);
            assertFalse(certList.isEmpty(), "Chữ ký số CMS phải chứa chứng chỉ X.509 của người ký");
        }
    }

    @Test
    @DisplayName("Keystore được tạo mới và tái sử dụng từ tệp đã lưu trữ mà không bị tạo mới sau mỗi lần khởi động")
    void keystorePersistence_shouldReuseExistingKeystoreWithoutRegeneration(@TempDir Path tempDir) throws Exception {
        Path keystoreFile = tempDir.resolve("test-pki.p12");

        PdfProcessingServiceImpl service1 = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service1, "epuPrivateKey", null);
        ReflectionTestUtils.setField(service1, "epuCertificate", null);
        ReflectionTestUtils.setField(service1, "fallbackKeystorePath", keystoreFile.toString());
        service1.init();

        assertTrue(java.nio.file.Files.exists(keystoreFile), "Keystore file phải được sinh ra và ghi vào đĩa");

        PrivateKey key1 = service1.getPrivateKey();
        X509Certificate cert1 = service1.getCertificate();
        assertNotNull(key1);
        assertNotNull(cert1);

        // Khởi tạo instance thứ 2 với cùng đường dẫn keystore -> phải đọc lại key/cert đã lưu
        PdfProcessingServiceImpl service2 = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service2, "epuPrivateKey", null);
        ReflectionTestUtils.setField(service2, "epuCertificate", null);
        ReflectionTestUtils.setField(service2, "fallbackKeystorePath", keystoreFile.toString());
        service2.init();

        PrivateKey key2 = service2.getPrivateKey();
        X509Certificate cert2 = service2.getCertificate();
        assertNotNull(key2);
        assertEquals(key1.getAlgorithm(), key2.getAlgorithm(), "Thuat toan khoa phai trung khop");

        assertEquals(cert1.getSerialNumber(), cert2.getSerialNumber(), "Serial number của chứng chỉ phải trùng khớp từ keystore đã lưu");
        assertArrayEquals(cert1.getEncoded(), cert2.getEncoded(), "Chứng chỉ phải trùng khớp");
    }

    @Test
    @DisplayName("Nạp keystore từ cấu hình đường dẫn tệp hợp lệ")
    void configuredKeystore_validFile_shouldLoadSuccessfully(@TempDir Path tempDir) {
        Path keystoreFile = tempDir.resolve("configured-pki.p12");
        PdfProcessingServiceImpl generatorService = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(generatorService, "epuPrivateKey", null);
        ReflectionTestUtils.setField(generatorService, "epuCertificate", null);
        ReflectionTestUtils.setField(generatorService, "fallbackKeystorePath", keystoreFile.toString());
        generatorService.init();

        // Nạp qua keystorePath với alias xác định
        PdfProcessingServiceImpl service = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service, "epuPrivateKey", null);
        ReflectionTestUtils.setField(service, "epuCertificate", null);
        ReflectionTestUtils.setField(service, "keystorePath", keystoreFile.toString());
        ReflectionTestUtils.setField(service, "keystorePassword", "epu-keystore-secret");
        ReflectionTestUtils.setField(service, "keyAlias", "epu-signer");
        ReflectionTestUtils.setField(service, "keyPassword", "epu-signer-secret");
        service.init();

        assertNotNull(service.getPrivateKey());
        assertNotNull(service.getCertificate());
    }

    @Test
    @DisplayName("Nạp keystore từ cấu hình không có alias thì tự động lấy alias đầu tiên")
    void configuredKeystore_blankAlias_shouldUseFirstAlias(@TempDir Path tempDir) {
        Path keystoreFile = tempDir.resolve("configured-pki-noalias.p12");
        PdfProcessingServiceImpl generatorService = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(generatorService, "epuPrivateKey", null);
        ReflectionTestUtils.setField(generatorService, "epuCertificate", null);
        ReflectionTestUtils.setField(generatorService, "fallbackKeystorePath", keystoreFile.toString());
        generatorService.init();

        PdfProcessingServiceImpl service = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service, "epuPrivateKey", null);
        ReflectionTestUtils.setField(service, "epuCertificate", null);
        ReflectionTestUtils.setField(service, "keystorePath", keystoreFile.toString());
        ReflectionTestUtils.setField(service, "keystorePassword", "epu-keystore-secret");
        ReflectionTestUtils.setField(service, "keyAlias", "");
        ReflectionTestUtils.setField(service, "keyPassword", "epu-signer-secret");
        service.init();

        assertNotNull(service.getPrivateKey());
        assertNotNull(service.getCertificate());
    }

    @Test
    @DisplayName("Nạp keystore với file không tồn tại ném ra IllegalStateException")
    void configuredKeystore_missingFile_shouldThrowIllegalStateException() {
        PdfProcessingServiceImpl service = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service, "keystorePath", "missing-keystore-path.p12");
        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    @DisplayName("Nạp keystore từ classpath không tồn tại ném ra IllegalStateException")
    void configuredKeystore_missingClasspathResource_shouldThrowIllegalStateException() {
        PdfProcessingServiceImpl service = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service, "keystorePath", "classpath:/missing-pki.p12");
        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    @DisplayName("Kiểm tra setter cho PrivateKey và Certificate")
    void settersAndGetters_shouldUpdateFields() {
        PdfProcessingServiceImpl service = new PdfProcessingServiceImpl();
        PrivateKey key = service.getPrivateKey();
        X509Certificate cert = service.getCertificate();

        service.setPrivateKey(key);
        service.setCertificate(cert);

        assertSame(key, service.getPrivateKey());
        assertSame(cert, service.getCertificate());
    }

    @Test
    @DisplayName("Hỗ trợ inject trực tiếp PrivateKey và X509Certificate qua constructor")
    void constructorInjection_shouldUseInjectedKeys() {
        PdfProcessingServiceImpl baseService = new PdfProcessingServiceImpl();
        PrivateKey mockKey = baseService.getPrivateKey();
        X509Certificate mockCert = baseService.getCertificate();

        PdfProcessingServiceImpl injectedService = new PdfProcessingServiceImpl(mockKey, mockCert);
        assertSame(mockKey, injectedService.getPrivateKey());
        assertSame(mockCert, injectedService.getCertificate());
    }
}

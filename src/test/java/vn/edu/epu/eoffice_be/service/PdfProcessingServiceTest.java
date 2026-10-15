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
        PdfProcessingServiceImpl impl = new PdfProcessingServiceImpl();
        impl.init();
        pdfProcessingService = impl;
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

            CMSSignedData cmsSignedData = new CMSSignedData(new java.io.ByteArrayInputStream(signatureBytes));
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
        service.init();
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
        baseService.init();
        PrivateKey mockKey = baseService.getPrivateKey();
        X509Certificate mockCert = baseService.getCertificate();

        PdfProcessingServiceImpl injectedService = new PdfProcessingServiceImpl(mockKey, mockCert);
        assertSame(mockKey, injectedService.getPrivateKey());
        assertSame(mockCert, injectedService.getCertificate());
    }

    @Test
    @DisplayName("Constructor không gọi init(), không ghi file hoặc sinh khóa trước khi tiêm @Value")
    void constructor_shouldNotWriteToFilesystemOrGenerateKeys(@TempDir Path tempDir) {
        Path fallbackFile = tempDir.resolve("should-not-exist.p12");

        // Khi chỉ gọi constructor (chưa tiêm @Value và chưa gọi @PostConstruct init())
        PdfProcessingServiceImpl service = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(service, "fallbackKeystorePath", fallbackFile.toString());

        // Key và cert phải là null, không có file nào bị ghi vào đĩa
        assertNull(service.getPrivateKey(), "PrivateKey phải là null trước khi init()");
        assertNull(service.getCertificate(), "Certificate phải là null trước khi init()");
        assertFalse(java.nio.file.Files.exists(fallbackFile), "Không được ghi file keystore fallback trong constructor");

        // Tạo sẵn một keystore hợp lệ để cấu hình keystorePath
        Path configuredKeystoreFile = tempDir.resolve("configured-pki.p12");
        PdfProcessingServiceImpl generator = new PdfProcessingServiceImpl();
        ReflectionTestUtils.setField(generator, "fallbackKeystorePath", configuredKeystoreFile.toString());
        generator.init();

        // Cấu hình keystorePath và gọi init() -> nạp từ keystorePath, KHÔNG tạo file fallback
        ReflectionTestUtils.setField(service, "keystorePath", configuredKeystoreFile.toString());
        ReflectionTestUtils.setField(service, "keystorePassword", "epu-keystore-secret");
        ReflectionTestUtils.setField(service, "keyAlias", "epu-signer");
        ReflectionTestUtils.setField(service, "keyPassword", "epu-signer-secret");
        service.init();

        assertNotNull(service.getPrivateKey(), "PrivateKey phải nạp thành công từ keystorePath");
        assertFalse(java.nio.file.Files.exists(fallbackFile), "Khi keystorePath đã cấu hình, không được tạo file fallback");
    }
}

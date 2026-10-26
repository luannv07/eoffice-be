package vn.edu.epu.eoffice_be.security.pki;

import vn.edu.epu.eoffice_be.common.enums.InitialSignStage;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.service.PkiUserKeystoreService;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * Lớp thực hiện ký số phê duyệt / ký nháy (Approval Signature) trên tài liệu PDF sử dụng Apache PDFBox 3.x
 * và BouncyCastle theo chuẩn PKCS#7 / CMS Detached.
 *
 * Các nguyên tắc an toàn mật mã cốt lõi:
 * 1. Chữ ký phê duyệt (Approval Signature), KHÔNG dùng DocMDP/certification để cho phép nhiều người ký sau.
 * 2. Lưu tăng dần (Incremental Save - saveIncrementalForExternalSigning) để bảo toàn nguyên vẹn
 *    các chữ ký số đã ký trước đó trong tài liệu.
 * 3. Tên widget field duy nhất dạng "sig_user_<userId>" để chống trùng lặp (1 người không ký 2 lần).
 * 4. Tạo chữ ký CMS detached nhúng đầy đủ chuỗi chứng thư (User + CA) và các thuộc tính có ký (Signed Attributes).
 */
@Slf4j
@Component
public class PdfSigner {

    private static final int PREFERRED_SIGNATURE_SIZE = 16384;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final PkiUserKeystoreService userKeystores;

    @Value("${eoffice.pki.signatures-dir:./uploads/signatures}")
    private String signaturesDir = "./uploads/signatures";

    @Value("${eoffice.pki.stamps-dir:./uploads/stamps}")
    private String stampsDir = "./uploads/stamps";

    public record SignResult(
            String userId,
            String signerCN,
            String serialHex,
            String outPdfSha256,
            Date signDate,
            String fieldName,
            Path outPdfPath,
            byte[] signedPdfBytes
    ) {}

    @Autowired
    public PdfSigner(PkiUserKeystoreService userKeystores) {
        Crypto.registerBouncyCastle();
        this.userKeystores = Objects.requireNonNull(userKeystores);
    }

    /** Ensure a reusable user keystore exists in the database. */
    public void ensureUserCertificate(String userId, String displayName) {
        String name = displayName;
        String title = null;
        if (displayName != null && displayName.contains(" - ")) {
            String[] parts = displayName.split(" - ", 2);
            name = parts[0].trim();
            title = parts[1].trim();
        }
        ensureUserCertificate(userId, name, title, null);
    }

    public void ensureUserCertificate(String userId, String displayName, String title, String orgUnit) {
        userKeystores.ensure(userId, displayName, title, orgUnit);
    }

    private Crypto.P12Bundle requireUserCertificate(String userId) {
        return userKeystores.require(userId);
    }
    /**
     * Thực hiện ký số 1 chữ ký vô hình (invisible signature) lên file PDF (theo đường dẫn file).
     */
    public SignResult sign(Path inPdf, Path outPdf, String userId, String reason) {
        Crypto.P12Bundle bundle = requireUserCertificate(userId);
        return signWithCustomKeys(inPdf, outPdf, userId, reason, bundle.privateKey(), bundle.chain());
    }

    /**
     * Ký số với PrivateKey và CertChain tùy chỉnh trên file PDF.
     */
    public SignResult signWithCustomKeys(Path inPdf, Path outPdf, String userId, String reason,
                                         PrivateKey privateKey, X509Certificate[] certChain) {
        return signWithFieldName(inPdf, outPdf, userId, reason, privateKey, certChain, "sig_user_" + userId);
    }

    public SignResult signInitialWithCustomKeys(Path inPdf, Path outPdf, String userId, String reason,
                                               PrivateKey privateKey, X509Certificate[] certChain,
                                               InitialSignStage stage) {
        Objects.requireNonNull(stage, "Signing stage is required");
        return signWithFieldName(inPdf, outPdf, userId, reason, privateKey, certChain, stage.fieldName(userId));
    }

    private SignResult signWithFieldName(Path inPdf, Path outPdf, String userId, String reason,
                                         PrivateKey privateKey, X509Certificate[] certChain, String targetFieldName) {
        Objects.requireNonNull(inPdf, "Đường dẫn file PDF đầu vào không được null");
        Objects.requireNonNull(outPdf, "Đường dẫn file PDF đầu ra không được null");
        Objects.requireNonNull(userId, "UserId không được null");
        Objects.requireNonNull(privateKey, "privateKey không được null");
        Objects.requireNonNull(certChain, "certChain không được null");

        if (!Files.exists(inPdf)) {
            throw new IllegalArgumentException("File PDF đầu vào không tồn tại: " + inPdf.toAbsolutePath());
        }

        if (inPdf.toAbsolutePath().normalize().equals(outPdf.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("File đầu ra không được trùng file đầu vào để đảm bảo không ghi đè file gốc.");
        }

        try {
            if (outPdf.getParent() != null) {
                Files.createDirectories(outPdf.getParent());
            }

            X509Certificate userCert = certChain[0];
            String signerCN = extractCommonName(userCert);
            String signerTitle = extractTitle(userCert);
            String displayName = (signerTitle != null && !signerTitle.isBlank())
                    ? signerCN + " - " + signerTitle
                    : signerCN;
            String serialHex = userCert.getSerialNumber().toString(16).toLowerCase();

            try (PDDocument document = Loader.loadPDF(inPdf.toFile())) {
                for (PDSignatureField existingField : document.getSignatureFields()) {
                    String fullyQualified = existingField.getFullyQualifiedName();
                    String partial = existingField.getPartialName();
                    if (targetFieldName.equals(fullyQualified) || targetFieldName.equals(partial)) {
                        throw new DuplicateSignatureException("User đã ký: " + userId + " (Trường chữ ký '" + targetFieldName + "' đã tồn tại trong tài liệu)");
                    }
                }

                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName(displayName);
                signature.setReason(reason != null && !reason.isBlank() ? reason : "Phê duyệt tài liệu điện tử");

                Calendar signDate = Calendar.getInstance();
                signature.setSignDate(signDate);

                SignatureOptions options = new SignatureOptions();
                options.setPreferredSignatureSize(PREFERRED_SIGNATURE_SIZE);

                document.addSignature(signature, options);

                for (PDSignatureField sigField : document.getSignatureFields()) {
                    if (sigField.getSignature() != null
                            && sigField.getSignature().getCOSObject() == signature.getCOSObject()) {
                        sigField.setPartialName(targetFieldName);
                        sigField.getCOSObject().setNeedToBeUpdated(true);
                        break;
                    }
                }

                try (OutputStream fos = Files.newOutputStream(outPdf)) {
                    ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(fos);

                    byte[] contentToSign;
                    try (InputStream is = externalSigning.getContent()) {
                        contentToSign = is.readAllBytes();
                    }

                    byte[] cmsSignature = createCmsSignature(contentToSign, privateKey, certChain);
                    externalSigning.setSignature(cmsSignature);
                }
            }

            byte[] outBytes = Files.readAllBytes(outPdf);
            String outPdfSha256 = Crypto.sha256Hex(outBytes);

            return new SignResult(
                    userId,
                    signerCN,
                    serialHex,
                    outPdfSha256,
                    new Date(),
                    "sig_user_" + userId,
                    outPdf,
                    outBytes
            );

        } catch (IllegalStateException | IllegalArgumentException e) {
            try {
                Files.deleteIfExists(outPdf);
            } catch (Exception ignored) {
            }
            throw e;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(outPdf);
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Lỗi khi thực hiện ký số tài liệu PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Ký số trực tiếp từ mảng byte[] (thuận tiện cho việc gọi từ Spring Service và FileStorageService).
     *
     * @param inPdfBytes Dữ liệu PDF gốc
     * @param userId     Mã định danh cán bộ ký
     * @param reason     Lý do ký
     * @return SignResult chứa dữ liệu byte[] đã ký và mã băm
     */
    public SignResult signBytes(byte[] inPdfBytes, String userId, String reason) {
        return signBytes(inPdfBytes, userId, null, reason);
    }

    /**
     * Ký số trực tiếp từ mảng byte[], tự động kiểm tra và cấp chứng thư số nếu user chưa có.
     */
    public SignResult signBytes(byte[] inPdfBytes, String userId, String displayName, String reason) {
        String name = displayName;
        String title = null;
        if (displayName != null && displayName.contains(" - ")) {
            String[] parts = displayName.split(" - ", 2);
            name = parts[0].trim();
            title = parts[1].trim();
        }
        return signBytes(inPdfBytes, userId, name, title, null, reason);
    }

    /**
     * Ký số trực tiếp từ mảng byte[], tự động kiểm tra và cấp chứng thư số kèm Chức vụ (TITLE) và Đơn vị (OU).
     */
    public SignResult signBytes(byte[] inPdfBytes, String userId, String displayName, String title, String orgUnit, String reason) {
        Objects.requireNonNull(inPdfBytes, "Dữ liệu PDF đầu vào không được null");
        ensureUserCertificate(userId, displayName, title, orgUnit);
        try {
            Path tempIn = Files.createTempFile("pki_in_", ".pdf");
            Path tempOut = Files.createTempFile("pki_out_", ".pdf");
            try {
                Files.write(tempIn, inPdfBytes);
                SignResult result = sign(tempIn, tempOut, userId, reason);
                return result;
            } finally {
                Files.deleteIfExists(tempIn);
                Files.deleteIfExists(tempOut);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi ký số PDF dạng byte[]: " + e.getMessage(), e);
        }
    }

    /**
     * Ký số trực tiếp từ mảng byte[] với PrivateKey và CertChain tùy chỉnh.
     */
    public SignResult signInitialBytes(byte[] pdf, String userId, String displayName, String title,
                                       String orgUnit, String reason, InitialSignStage stage) {
        Objects.requireNonNull(pdf, "PDF is required");
        Objects.requireNonNull(stage, "Signing stage is required");
        ensureUserCertificate(userId, displayName, title, orgUnit);
        Crypto.P12Bundle bundle = requireUserCertificate(userId);
        try {
            Path input = Files.createTempFile("initial_in_", ".pdf");
            try {
                Path output = Files.createTempFile("initial_out_", ".pdf");
                try {
                    Files.write(input, pdf);
                    return signInitialWithCustomKeys(input, output, userId, reason,
                            bundle.privateKey(), bundle.chain(), stage);
                } finally {
                    Files.deleteIfExists(output);
                }
            } finally {
                Files.deleteIfExists(input);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Không thể ký nháy PDF", e);
        }
    }

    public SignResult signBytesWithCustomKeys(byte[] inPdfBytes, String userId, String reason,
                                              PrivateKey privateKey, X509Certificate[] certChain) {
        Objects.requireNonNull(inPdfBytes, "Dữ liệu PDF đầu vào không được null");
        try {
            Path tempIn = Files.createTempFile("pki_in_", ".pdf");
            Path tempOut = Files.createTempFile("pki_out_", ".pdf");
            try {
                Files.write(tempIn, inPdfBytes);
                SignResult result = signWithCustomKeys(tempIn, tempOut, userId, reason, privateKey, certChain);
                return result;
            } finally {
                Files.deleteIfExists(tempIn);
                Files.deleteIfExists(tempOut);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi ký số PDF dạng byte[]: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo chữ ký số CMS / PKCS#7 detached bằng BouncyCastle:
     * - Thuật toán: SHA256withRSA.
     * - Nhúng toàn bộ chuỗi chứng thư (User Cert + CA Cert).
     * - Chứa các thuộc tính có ký tiêu chuẩn (contentType, messageDigest, signingTime).
     */
    public static byte[] createCmsSignature(byte[] contentToSign, PrivateKey privateKey, X509Certificate[] chain) throws Exception {
        X509Certificate signerCert = chain[0];

        JcaCertStore certStore = new JcaCertStore(Arrays.asList(chain));

        ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey);

        SignerInfoGenerator signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build())
                .build(contentSigner, signerCert);

        CMSSignedDataGenerator signedDataGenerator = new CMSSignedDataGenerator();
        signedDataGenerator.addSignerInfoGenerator(signerInfoGenerator);
        signedDataGenerator.addCertificates(certStore);

        CMSProcessableByteArray processableData = new CMSProcessableByteArray(contentToSign);
        CMSSignedData cmsSignedData = signedDataGenerator.generate(processableData, false);

        return cmsSignedData.getEncoded();
    }

    /**
     * Trích xuất Common Name (CN) từ Subject của chứng thư số.
     */
    public static String extractCommonName(X509Certificate cert) {
        try {
            X500Name x500name = new JcaX509CertificateHolder(cert).getSubject();
            org.bouncycastle.asn1.x500.RDN[] rdns = x500name.getRDNs(BCStyle.CN);
            if (rdns.length > 0) {
                return IETFUtils.valueToString(rdns[0].getFirst().getValue());
            }
        } catch (Exception ignored) {
        }
        return cert.getSubjectX500Principal().getName();
    }

    /**
     * Trích xuất Title (TITLE - Chức vụ) từ Subject của chứng thư số.
     */
    public static String extractTitle(X509Certificate cert) {
        try {
            X500Name x500name = new JcaX509CertificateHolder(cert).getSubject();
            org.bouncycastle.asn1.x500.RDN[] rdns = x500name.getRDNs(BCStyle.T);
            if (rdns != null && rdns.length > 0) {
                return IETFUtils.valueToString(rdns[0].getFirst().getValue());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Trích xuất Organizational Unit (OU - Phòng ban/Đơn vị) từ Subject của chứng thư số.
     */
    public static String extractOrgUnit(X509Certificate cert) {
        try {
            X500Name x500name = new JcaX509CertificateHolder(cert).getSubject();
            org.bouncycastle.asn1.x500.RDN[] rdns = x500name.getRDNs(BCStyle.OU);
            if (rdns != null && rdns.length > 0) {
                return IETFUtils.valueToString(rdns[0].getFirst().getValue());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Ký số hiển thị (Visible Signature) lên file PDF (theo đường dẫn file) kèm tọa độ và hình ảnh con dấu/chữ ký.
     * Lưu tăng dần (incremental save), bảo toàn chữ ký cũ, CHƯA áp dụng khóa DocMDP P=1 để cho phép các bước ký tiếp theo.
     */
    public SignResult signVisible(Path inPdf, Path outPdf, String userId, String reason, SignaturePosition pos) {
        Crypto.P12Bundle bundle = requireUserCertificate(userId);
        return signVisibleWithCustomKeys(inPdf, outPdf, userId, reason, bundle.privateKey(), bundle.chain(), pos);
    }

    /**
     * Ký số hiển thị với PrivateKey và CertChain tùy chỉnh, hỗ trợ tọa độ và nhúng ảnh con dấu/chữ ký.
     */
    public SignResult signVisibleWithCustomKeys(Path inPdf, Path outPdf, String userId, String reason,
                                                PrivateKey privateKey, X509Certificate[] certChain,
                                                SignaturePosition pos) {
        Objects.requireNonNull(inPdf, "Đường dẫn file PDF đầu vào không được null");
        Objects.requireNonNull(outPdf, "Đường dẫn file PDF đầu ra không được null");
        Objects.requireNonNull(userId, "UserId không được null");
        Objects.requireNonNull(privateKey, "privateKey không được null");
        Objects.requireNonNull(certChain, "certChain không được null");

        if (!Files.exists(inPdf)) {
            throw new IllegalArgumentException("File PDF đầu vào không tồn tại: " + inPdf.toAbsolutePath());
        }

        if (inPdf.toAbsolutePath().normalize().equals(outPdf.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("File đầu ra không được trùng file đầu vào để đảm bảo không ghi đè file gốc.");
        }

        try {
            if (outPdf.getParent() != null) {
                Files.createDirectories(outPdf.getParent());
            }

            X509Certificate userCert = certChain[0];
            String signerCN = extractCommonName(userCert);
            String signerTitle = extractTitle(userCert);
            String displayName = (signerTitle != null && !signerTitle.isBlank())
                    ? signerCN + " - " + signerTitle
                    : signerCN;
            String serialHex = userCert.getSerialNumber().toString(16).toLowerCase();
            String targetFieldName = "sig_approve_" + userId;

            try (PDDocument document = Loader.loadPDF(inPdf.toFile())) {
                for (PDSignatureField existingField : document.getSignatureFields()) {
                    String fullyQualified = existingField.getFullyQualifiedName();
                    String partial = existingField.getPartialName();
                    if (targetFieldName.equals(fullyQualified) || targetFieldName.equals(partial)) {
                        throw new DuplicateSignatureException("User đã ký phê duyệt: " + userId + " (Trường chữ ký '" + targetFieldName + "' đã tồn tại trong tài liệu)");
                    }
                }

                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName(displayName);
                signature.setLocation("Ha Noi, Viet Nam");
                signature.setReason(reason != null && !reason.isBlank() ? reason : "Phê duyệt văn bản điện tử");

                Calendar signDate = Calendar.getInstance();
                signature.setSignDate(signDate);

                SignatureOptions options = new SignatureOptions();
                options.setPreferredSignatureSize(PREFERRED_SIGNATURE_SIZE);

                document.addSignature(signature, options);

                for (PDSignatureField sigField : document.getSignatureFields()) {
                    if (sigField.getSignature() != null
                            && sigField.getSignature().getCOSObject() == signature.getCOSObject()) {
                        sigField.setPartialName(targetFieldName);
                        sigField.getCOSObject().setNeedToBeUpdated(true);

                        if (document.getNumberOfPages() > 0) {
                            int pageIdx;
                            if (pos != null && pos.page() > 0 && pos.page() < 9999) {
                                pageIdx = Math.max(0, Math.min(pos.page() - 1, document.getNumberOfPages() - 1));
                            } else {
                                pageIdx = Math.max(0, document.getNumberOfPages() - 1);
                            }

                            float x = (pos != null && pos.x() > 0) ? pos.x() : 380f;
                            float y = (pos != null && pos.y() > 0) ? pos.y() : 120f;
                            float width = (pos != null && pos.width() > 0) ? pos.width() : 150f;
                            float height = (pos != null && pos.height() > 0) ? pos.height() : 80f;

                            SignaturePosition effectivePos = new SignaturePosition(pageIdx + 1, x, y, width, height);

                            PDPage page = document.getPage(pageIdx);
                            PDAnnotationWidget widget = new PDAnnotationWidget();
                            PDRectangle rect = new PDRectangle(x, y, width, height);
                            widget.setRectangle(rect);
                            widget.setPage(page);
                            widget.setPrinted(true);
                            page.getAnnotations().add(widget);
                            sigField.getWidgets().add(widget);
                            page.getCOSObject().setNeedToBeUpdated(true);

                            // Chèn hình ảnh con dấu/chữ ký nếu có trên hệ thống
                            stampVisualImageIfPresent(document, page, userId, effectivePos);
                        }

                        break;
                    }
                }

                try (OutputStream fos = Files.newOutputStream(outPdf)) {
                    ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(fos);

                    byte[] contentToSign;
                    try (InputStream is = externalSigning.getContent()) {
                        contentToSign = is.readAllBytes();
                    }

                    byte[] cmsSignature = createCmsSignature(contentToSign, privateKey, certChain);
                    externalSigning.setSignature(cmsSignature);
                }
            }

            byte[] outBytes = Files.readAllBytes(outPdf);
            String outPdfSha256 = Crypto.sha256Hex(outBytes);

            return new SignResult(
                    userId,
                    signerCN,
                    serialHex,
                    outPdfSha256,
                    new Date(),
                    targetFieldName,
                    outPdf,
                    outBytes
            );

        } catch (IllegalStateException | IllegalArgumentException e) {
            try {
                Files.deleteIfExists(outPdf);
            } catch (Exception ignored) {
            }
            throw e;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(outPdf);
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Lỗi khi ký số hiển thị tài liệu PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Ký số hiển thị trực tiếp từ mảng byte[] kèm chức vụ, đơn vị và tọa độ.
     */
    public SignResult signVisibleBytes(byte[] inPdfBytes, String userId, String displayName, String title, String orgUnit, String reason, SignaturePosition pos) {
        Objects.requireNonNull(inPdfBytes, "Dữ liệu PDF đầu vào không được null");
        ensureUserCertificate(userId, displayName, title, orgUnit);
        try {
            Path tempIn = Files.createTempFile("pki_vis_in_", ".pdf");
            Path tempOut = Files.createTempFile("pki_vis_out_", ".pdf");
            try {
                Files.write(tempIn, inPdfBytes);
                return signVisible(tempIn, tempOut, userId, reason, pos);
            } finally {
                Files.deleteIfExists(tempIn);
                Files.deleteIfExists(tempOut);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi ký số hiển thị PDF dạng byte[]: " + e.getMessage(), e);
        }
    }

    public SignResult signVisibleBytes(byte[] inPdfBytes, String userId, String displayName, String reason, SignaturePosition pos) {
        String name = displayName;
        String title = null;
        if (displayName != null && displayName.contains(" - ")) {
            String[] parts = displayName.split(" - ", 2);
            name = parts[0].trim();
            title = parts[1].trim();
        }
        return signVisibleBytes(inPdfBytes, userId, name, title, null, reason, pos);
    }

    public SignResult signVisibleBytes(byte[] inPdfBytes, String userId, String displayName, String reason) {
        return signVisibleBytes(inPdfBytes, userId, displayName, reason, null);
    }

    /**
     * Ký phát hành văn bản chính thức kèm khóa niêm phong DocMDP (P=1: CERTIFIED_NO_CHANGES_ALLOWED)
     * và hỗ trợ tọa độ hiển thị con dấu (SignaturePosition).
     */
    public SignResult signAndCertify(Path inPdf, Path outPdf, String userId, String reason, SignaturePosition pos) {
        Crypto.P12Bundle bundle = requireUserCertificate(userId);
        return signAndCertifyWithCustomKeys(inPdf, outPdf, userId, reason, bundle.privateKey(), bundle.chain(), pos);
    }

    /**
     * Ký phát hành với PrivateKey và CertChain tùy chỉnh, thiết lập khóa DocMDP P=1.
     */
    public SignResult signAndCertifyWithCustomKeys(Path inPdf, Path outPdf, String userId, String reason,
                                                  PrivateKey privateKey, X509Certificate[] certChain,
                                                  SignaturePosition pos) {
        Objects.requireNonNull(inPdf, "Đường dẫn file PDF đầu vào không được null");
        Objects.requireNonNull(outPdf, "Đường dẫn file PDF đầu ra không được null");
        Objects.requireNonNull(userId, "UserId không được null");
        Objects.requireNonNull(privateKey, "privateKey không được null");
        Objects.requireNonNull(certChain, "certChain không được null");

        if (!Files.exists(inPdf)) {
            throw new IllegalArgumentException("File PDF đầu vào không tồn tại: " + inPdf.toAbsolutePath());
        }

        if (inPdf.toAbsolutePath().normalize().equals(outPdf.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("File đầu ra không được trùng file đầu vào để đảm bảo không ghi đè file gốc.");
        }

        try {
            if (outPdf.getParent() != null) {
                Files.createDirectories(outPdf.getParent());
            }

            X509Certificate userCert = certChain[0];
            String signerCN = extractCommonName(userCert);
            String signerTitle = extractTitle(userCert);
            String displayName = (signerTitle != null && !signerTitle.isBlank())
                    ? signerCN + " - " + signerTitle
                    : signerCN;
            String serialHex = userCert.getSerialNumber().toString(16).toLowerCase();

            try (PDDocument document = Loader.loadPDF(inPdf.toFile())) {
                String targetFieldName = "sig_publish_" + userId;
                for (PDSignatureField existingField : document.getSignatureFields()) {
                    String fullyQualified = existingField.getFullyQualifiedName();
                    String partial = existingField.getPartialName();
                    if (targetFieldName.equals(fullyQualified) || targetFieldName.equals(partial)) {
                        throw new DuplicateSignatureException("User đã ký phát hành: " + userId + " (Trường chữ ký '" + targetFieldName + "' đã tồn tại trong tài liệu)");
                    }
                }

                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName(displayName);
                signature.setLocation("Ha Noi, Viet Nam");
                signature.setReason(reason != null && !reason.isBlank() ? reason : "Ban hành văn bản điện tử");

                Calendar signDate = Calendar.getInstance();
                signature.setSignDate(signDate);

                // Thiết lập cờ niêm phong DocMDP CERTIFIED_NO_CHANGES_ALLOWED (P=1)
                setCertificationPermissions(document, signature, 1);

                SignatureOptions options = new SignatureOptions();
                options.setPreferredSignatureSize(PREFERRED_SIGNATURE_SIZE);

                document.addSignature(signature, options);

                for (PDSignatureField sigField : document.getSignatureFields()) {
                    if (sigField.getSignature() != null
                            && sigField.getSignature().getCOSObject() == signature.getCOSObject()) {
                        sigField.setPartialName(targetFieldName);
                        sigField.getCOSObject().setNeedToBeUpdated(true);

                        // Thêm khóa FieldMDP SigFieldLock: Action=All
                        COSDictionary lock = new COSDictionary();
                        lock.setItem(COSName.TYPE, COSName.getPDFName("SigFieldLock"));
                        lock.setName(COSName.getPDFName("Action"), "All");
                        sigField.getCOSObject().setItem(COSName.getPDFName("Lock"), lock);

                        // Hỗ trợ tọa độ visual signature nếu pos != null
                        if (pos != null && document.getNumberOfPages() > 0) {
                            int pageIdx = Math.max(0, Math.min(pos.page() - 1, document.getNumberOfPages() - 1));
                            PDPage page = document.getPage(pageIdx);
                            PDAnnotationWidget widget = new PDAnnotationWidget();
                            PDRectangle rect = new PDRectangle(pos.x(), pos.y(), pos.width(), pos.height());
                            widget.setRectangle(rect);
                            widget.setPage(page);
                            widget.setPrinted(true);
                            page.getAnnotations().add(widget);
                            sigField.getWidgets().add(widget);
                            page.getCOSObject().setNeedToBeUpdated(true);

                            // Chèn hình ảnh con dấu/chữ ký nếu có trên hệ thống (Cách A)
                            stampVisualImageIfPresent(document, page, userId, pos);
                        }

                        break;
                    }
                }

                try (OutputStream fos = Files.newOutputStream(outPdf)) {
                    ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(fos);

                    byte[] contentToSign;
                    try (InputStream is = externalSigning.getContent()) {
                        contentToSign = is.readAllBytes();
                    }

                    byte[] cmsSignature = createCmsSignature(contentToSign, privateKey, certChain);
                    externalSigning.setSignature(cmsSignature);
                }
            }

            byte[] outBytes = Files.readAllBytes(outPdf);
            String outPdfSha256 = Crypto.sha256Hex(outBytes);

            return new SignResult(
                    userId,
                    signerCN,
                    serialHex,
                    outPdfSha256,
                    new Date(),
                    "sig_publish_" + userId,
                    outPdf,
                    outBytes
            );

        } catch (IllegalStateException | IllegalArgumentException e) {
            try {
                Files.deleteIfExists(outPdf);
            } catch (Exception ignored) {
            }
            throw e;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(outPdf);
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Lỗi khi ký phát hành tài liệu PDF: " + e.getMessage(), e);
        }
    }

    public SignResult signAndCertifyBytes(byte[] inPdfBytes, String userId, String displayName, String title, String orgUnit, String reason, SignaturePosition pos) {
        Objects.requireNonNull(inPdfBytes, "Dữ liệu PDF đầu vào không được null");
        ensureUserCertificate(userId, displayName, title, orgUnit);
        try {
            Path tempIn = Files.createTempFile("pki_cert_in_", ".pdf");
            Path tempOut = Files.createTempFile("pki_cert_out_", ".pdf");
            try {
                Files.write(tempIn, inPdfBytes);
                return signAndCertify(tempIn, tempOut, userId, reason, pos);
            } finally {
                Files.deleteIfExists(tempIn);
                Files.deleteIfExists(tempOut);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi ký phát hành PDF dạng byte[]: " + e.getMessage(), e);
        }
    }

    public SignResult signAndCertifyBytes(byte[] inPdfBytes, String userId, String displayName, String reason) {
        return signAndCertifyBytes(inPdfBytes, userId, displayName, null, null, reason, null);
    }

    public SignResult signAndCertifyBytes(byte[] inPdfBytes, String userId, String displayName, String reason, SignaturePosition pos) {
        return signAndCertifyBytes(inPdfBytes, userId, displayName, null, null, reason, pos);
    }

    private void setCertificationPermissions(PDDocument doc, PDSignature sig, int accessPermissions) {
        COSDictionary sigDict = sig.getCOSObject();
        COSDictionary transformParams = new COSDictionary();
        transformParams.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"));
        transformParams.setInt(COSName.P, accessPermissions);
        transformParams.setName(COSName.V, "1.2");

        COSDictionary sigRef = new COSDictionary();
        sigRef.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        sigRef.setItem(COSName.getPDFName("TransformMethod"), COSName.getPDFName("DocMDP"));
        sigRef.setItem(COSName.getPDFName("TransformParams"), transformParams);

        COSArray referenceArray = new COSArray();
        referenceArray.add(sigRef);
        sigDict.setItem(COSName.getPDFName("Reference"), referenceArray);

        COSDictionary perms = new COSDictionary();
        perms.setItem(COSName.getPDFName("DocMDP"), sigDict);
        doc.getDocumentCatalog().getCOSObject().setItem(COSName.PERMS, perms);
        doc.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);
    }

    private void stampVisualImageIfPresent(PDDocument document, PDPage page, String userId, SignaturePosition pos) {
        Path imagePath = resolveSignatureImagePath(userId);
        if (imagePath != null && Files.exists(imagePath)) {
            try {
                PDImageXObject pdImage = PDImageXObject.createFromFile(imagePath.toString(), document);
                try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.drawImage(pdImage, pos.x(), pos.y(), pos.width(), pos.height());
                }
                page.getCOSObject().setNeedToBeUpdated(true);
                log.info("Đã chèn hình ảnh con dấu/chữ ký từ {} vào trang PDF tại ({}, {}, {}, {})",
                        imagePath, pos.x(), pos.y(), pos.width(), pos.height());
            } catch (Exception e) {
                log.warn("Không thể chèn hình ảnh con dấu/chữ ký từ {}: {}", imagePath, e.getMessage());
            }
        }
    }

    private Path resolveSignatureImagePath(String userId) {
        if (signaturesDir != null && userId != null) {
            Path userSigPng = Paths.get(signaturesDir, "user_" + userId + ".png");
            if (Files.exists(userSigPng)) return userSigPng;

            Path userSigJpg = Paths.get(signaturesDir, "user_" + userId + ".jpg");
            if (Files.exists(userSigJpg)) return userSigJpg;
        }

        if (stampsDir != null) {
            Path epuSealPng = Paths.get(stampsDir, "epu_seal.png");
            if (Files.exists(epuSealPng)) return epuSealPng;

            Path epuSealJpg = Paths.get(stampsDir, "epu_seal.jpg");
            if (Files.exists(epuSealJpg)) return epuSealJpg;

            Path sealPng = Paths.get(stampsDir, "seal.png");
            if (Files.exists(sealPng)) return sealPng;

            try {
                Path dir = Paths.get(stampsDir);
                if (Files.exists(dir)) {
                    try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                        java.util.Optional<Path> anyImg = stream
                                .filter(p -> {
                                    String name = p.getFileName().toString().toLowerCase();
                                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                                })
                                .findFirst();
                        if (anyImg.isPresent()) {
                            return anyImg.get();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}

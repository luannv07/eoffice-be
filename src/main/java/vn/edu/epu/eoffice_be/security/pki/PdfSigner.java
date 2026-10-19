package vn.edu.epu.eoffice_be.security.pki;

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
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
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
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.service.PkiCaService;
import vn.edu.epu.eoffice_be.service.PkiSigningHistory;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.FileSystemException;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
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

    private final PkiProperties pkiProperties;
    private final PkiCaService caService;
    private final PkiSigningHistory signingHistory;

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
    public PdfSigner(PkiProperties pkiProperties, PkiCaService caService, PkiSigningHistory signingHistory) {
        Crypto.registerBouncyCastle();
        this.pkiProperties = Objects.requireNonNull(pkiProperties);
        this.caService = Objects.requireNonNull(caService);
        this.signingHistory = Objects.requireNonNull(signingHistory);
    }

    /**
     * Tự động kiểm tra và cấp phát chứng thư số cho User nếu chưa có file .p12.
     * Tự động trích xuất Chức vụ nếu displayName có định dạng "Họ tên - Chức vụ".
     */
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

    /**
     * Tự động kiểm tra và cấp phát chứng thư số cho User nếu chưa có file .p12.
     * Hỗ trợ nhúng Chức vụ (TITLE) và Phòng ban/Đơn vị (OU) theo chuẩn X.509 v3.
     */
    public void ensureUserCertificate(String userId, String displayName, String title, String orgUnit) {
        UserMaterial material = userMaterial(userId);
        Crypto.P12Bundle caBundle = caService.requireValidCa();
        try {
            PkiFileSupport.ensurePrivateDirectory(pkiProperties.userKeystores());
            PkiFileSupport.withLock(material.lock(), () -> {
                boolean hasP12 = Files.exists(material.p12(), LinkOption.NOFOLLOW_LINKS);
                boolean hasCertificate = Files.exists(material.certificate(), LinkOption.NOFOLLOW_LINKS);
                if (hasP12 && hasCertificate) {
                    validateUserMaterial(material, userId, caBundle);
                    return null;
                }
                if (hasP12 || hasCertificate) {
                    throw invalidUserMaterial("Bộ khóa người dùng chưa đầy đủ; cần khôi phục bản sao lưu.");
                }
                if (hasUserTemporaryMaterial(material)) {
                    throw invalidUserMaterial("Phát hiện tệp khóa tạm của người dùng; cần kiểm tra hoặc khôi phục bộ khóa trước khi cấp lại.");
                }
                if (signingHistory.hasUserSigningHistory(userId)) {
                    throw new BusinessException(ErrorCode.PKI_USER_KEYSTORE_INVALID,
                            "Người dùng đã có lịch sử ký nhưng không còn bộ khóa; cần khôi phục bản sao lưu.");
                }
                issueUserCertificate(material, userId, displayName, title, orgUnit, caBundle);
                return null;
            });
        } catch (IOException | SecurityException | DataAccessException e) {
            throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
        }
    }

    private void issueUserCertificate(UserMaterial material, String userId, String displayName,
                                      String title, String orgUnit, Crypto.P12Bundle caBundle) throws IOException {
        Path temporaryP12 = null;
        Path temporaryCertificate = null;
        char[] password = pkiProperties.password();
        try {
            X509Certificate caCert = caBundle.getEndEntityCertificate();
            PrivateKey caKey = caBundle.privateKey();

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(2048, new SecureRandom());
            KeyPair userPair = kpg.generateKeyPair();

            BigInteger serial = new BigInteger(64, new SecureRandom());
            if (serial.compareTo(BigInteger.ZERO) <= 0) {
                serial = serial.abs().add(BigInteger.ONE);
            }

            Instant now = Instant.now();
            Instant notBeforeInstant = now.minus(5, ChronoUnit.MINUTES);
            if (notBeforeInstant.isBefore(caCert.getNotBefore().toInstant())) {
                notBeforeInstant = caCert.getNotBefore().toInstant();
            }
            Date notBefore = Date.from(notBeforeInstant);
            Instant notAfterInstant = now.plus(365 * 3, ChronoUnit.DAYS);
            if (notAfterInstant.isAfter(caCert.getNotAfter().toInstant())) {
                notAfterInstant = caCert.getNotAfter().toInstant();
            }
            Date notAfter = Date.from(notAfterInstant);

            String safeName = (displayName != null && !displayName.isBlank()) ? displayName : "Can bo " + userId;
            X500NameBuilder subBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            subBuilder.addRDN(BCStyle.C, "VN");
            subBuilder.addRDN(BCStyle.O, "Truong Dai hoc Dien Luc - EPU");
            if (orgUnit != null && !orgUnit.isBlank()) {
                subBuilder.addRDN(BCStyle.OU, orgUnit);
            }
            if (title != null && !title.isBlank()) {
                subBuilder.addRDN(BCStyle.T, title);
            }
            subBuilder.addRDN(BCStyle.UID, userId);
            subBuilder.addRDN(BCStyle.CN, safeName);
            X500Name subject = subBuilder.build();
            X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, userPair.getPublic());

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(userPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caCert));

            ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(caKey);
            X509CertificateHolder holder = certBuilder.build(contentSigner);
            X509Certificate userCert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);

            Certificate[] chain = new Certificate[]{userCert, caCert};
            temporaryP12 = PkiFileSupport.createTemp(material.p12());
            temporaryCertificate = PkiFileSupport.createTemp(material.certificate());
            Crypto.saveP12(temporaryP12, password, "user", userPair.getPrivate(), chain);
            Crypto.saveCertPem(temporaryCertificate, userCert);
            validateUserMaterial(new UserMaterial(temporaryP12, temporaryCertificate, material.lock()), userId, caBundle);
            // A partial publication is deliberately retained for recovery, never replaced with new keys.
            PkiFileSupport.publishNew(temporaryP12, material.p12());
            PkiFileSupport.publishNew(temporaryCertificate, material.certificate());
            log.info("Tu dong cap phat thanh cong chung thu so PKI cho User [{}] - {}", userId, safeName);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Không thể cấp chứng thư cho user {} ({})", userId, e.getClass().getSimpleName());
            if (isStorageFailure(e)) throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
            throw invalidUserMaterial("Không thể cấp chứng thư người dùng.");
        } finally {
            Arrays.fill(password, '\0');
            deleteTemporary(temporaryP12);
            deleteTemporary(temporaryCertificate);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary != null) {
            try { Files.deleteIfExists(temporary); }
            catch (IOException ignored) { /* Preserve the actual result; never remove published key material. */ }
        }
    }

    private record UserMaterial(Path p12, Path certificate, Path lock) {}

    private boolean hasUserTemporaryMaterial(UserMaterial material) throws IOException {
        String p12Name = material.p12().getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        String certificateName = material.certificate().getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        try (var entries = Files.newDirectoryStream(material.p12().getParent())) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (isTemporaryFor(name, p12Name) || isTemporaryFor(name, certificateName)) return true;
            }
        }
        return false;
    }

    private boolean isTemporaryFor(String name, String targetName) {
        return name.equals(targetName + ".tmp")
                || (name.startsWith("." + targetName + ".") && name.endsWith(".tmp"));
    }

    private UserMaterial userMaterial(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Mã người ký không hợp lệ.");
        }
        Path directory = pkiProperties.userKeystores();
        return new UserMaterial(directory.resolve("user_" + userId + ".p12"),
                directory.resolve("user_" + userId + ".crt"), directory.resolve(".user_" + userId + ".lock"));
    }

    private Crypto.P12Bundle requireUserCertificate(String userId) {
        Crypto.P12Bundle caBundle = caService.requireValidCa();
        UserMaterial material = userMaterial(userId);
        if (!Files.exists(material.p12(), LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(material.certificate(), LinkOption.NOFOLLOW_LINKS)) {
            throw invalidUserMaterial("Không tìm thấy bộ khóa người dùng; cần khôi phục bản sao lưu hoặc ký lần đầu qua luồng cấp chứng thư.");
        }
        return validateUserMaterial(material, userId, caBundle);
    }

    private Crypto.P12Bundle validateUserMaterial(UserMaterial material, String userId, Crypto.P12Bundle caBundle) {
        char[] password = pkiProperties.password();
        try {
            if (!Files.exists(material.p12(), LinkOption.NOFOLLOW_LINKS)
                    || !Files.exists(material.certificate(), LinkOption.NOFOLLOW_LINKS)) {
                throw new GeneralSecurityException("Bộ khóa không đầy đủ.");
            }
            if (!Files.readAttributes(material.p12(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()
                    || !Files.readAttributes(material.certificate(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
                throw new GeneralSecurityException("Bộ khóa không đầy đủ hoặc chứa liên kết tệp.");
            }
            Crypto.P12Bundle bundle = Crypto.loadP12(material.p12(), password);
            X509Certificate leaf = bundle.getEndEntityCertificate();
            leaf.checkValidity();
            X500Name subject = X500Name.getInstance(leaf.getSubjectX500Principal().getEncoded());
            var uids = subject.getRDNs(BCStyle.UID);
            if (uids.length != 1 || !userId.equals(IETFUtils.valueToString(uids[0].getFirst().getValue()))) {
                throw new GeneralSecurityException("UID chứng thư không khớp người ký.");
            }
            boolean[] usage = leaf.getKeyUsage();
            if (leaf.getBasicConstraints() != -1 || usage == null || usage.length < 2 || !usage[0] || !usage[1]) {
                throw new GeneralSecurityException("Chứng thư không có quyền ký hợp lệ.");
            }
            X509Certificate caCertificate = caBundle.getEndEntityCertificate();
            if (bundle.chain().length != 2
                    || !PkiCaService.certificateFingerprint(bundle.chain()[1])
                    .equals(PkiCaService.certificateFingerprint(caCertificate))
                    || !leaf.getIssuerX500Principal().equals(caCertificate.getSubjectX500Principal())) {
                throw new GeneralSecurityException("Chứng thư không thuộc Root CA hiện tại.");
            }
            leaf.verify(caCertificate.getPublicKey());
            PkiCaService.verifyKeyPair(bundle);
            if (!Arrays.equals(leaf.getEncoded(), Crypto.loadCertPem(material.certificate()).getEncoded())) {
                throw new GeneralSecurityException("Chứng thư công khai không khớp bộ khóa.");
            }
            return bundle;
        } catch (CertificateExpiredException e) {
            throw invalidUserMaterial("Chứng thư người dùng đã hết hạn; cần quản trị viên xử lý, không được tự tạo lại bộ khóa.");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Bộ khóa không hợp lệ của user {} ({})", userId, e.getClass().getSimpleName());
            if (isStorageFailure(e)) throw new BusinessException(ErrorCode.PKI_STORAGE_ERROR);
            throw invalidUserMaterial("Bộ khóa người dùng không hợp lệ; cần kiểm tra hoặc khôi phục bản sao lưu.");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private BusinessException invalidUserMaterial(String message) {
        return new BusinessException(ErrorCode.PKI_USER_KEYSTORE_INVALID, message);
    }

    private boolean isStorageFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof FileSystemException || cause instanceof SecurityException) return true;
        }
        return false;
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
                String targetFieldName = "sig_user_" + userId;
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

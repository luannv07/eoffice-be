package vn.edu.epu.eoffice_be.security.pki;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.SignatureItemResponse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bộ kiểm định và xác thực chữ ký số trên tài liệu PDF (PDF Verification Engine).
 *
 * Kiểm tra 7 tiêu chí bảo mật toàn diện cho từng chữ ký trong tài liệu:
 * 1. INTEGRITY: Tính toán SHA-256 trên đúng ByteRange, so khớp với messageDigest trong CMS signed attributes,
 *    và xác minh chữ ký mật mã CMS bằng khóa công khai của người ký.
 * 2. CHAIN: Kiểm tra chứng thư người ký có được cấp phát và ký hợp lệ bởi Root CA nội bộ (ca.crt),
 *    ngăn chặn chứng thư tự ký hoặc mạo danh.
 * 3. VALIDITY: Kiểm tra thời hạn hiệu lực tại thời điểm ký (signingTime) và tại thời điểm hiện tại.
 * 4. KEYUSAGE: Kiểm tra BasicConstraints CA=false và KeyUsage có digitalSignature + nonRepudiation.
 * 5. REVOCATION: Kiểm tra số serial có nằm trong danh sách thu hồi CRL (revoked.json).
 * 6. COVERAGE: Kiểm tra vùng bao phủ ByteRange đối với file gốc và các khối cập nhật tăng dần (Incremental Updates).
 * 7. IDENTITY: Đối chiếu trường UID trong chứng thư với tên field "sig_user_<id>".
 */
@Slf4j
@Component
public class PdfSignatureVerifier {

    @Value("${eoffice.pki.ca-cert-path:./uploads/keystores/ca/ca.crt}")
    private String caCertPath = "./uploads/keystores/ca/ca.crt";

    @Value("${eoffice.pki.revoked-path:./uploads/keystores/ca/revoked.json}")
    private String revokedPath = "./uploads/keystores/ca/revoked.json";

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    public PdfSignatureVerifier() {
        Crypto.registerBouncyCastle();
    }

    public PdfSignatureVerifier(String caCertPath, String revokedPath) {
        Crypto.registerBouncyCastle();
        this.caCertPath = caCertPath;
        this.revokedPath = revokedPath;
    }

    /**
     * Xác thực các chữ ký số trong file PDF từ mảng bytes.
     *
     * @param pdfBytes Mảng bytes của file PDF cần kiểm định
     * @return Kết quả kiểm định chi tiết toàn bộ chữ ký
     */
    public DocumentSignatureVerifyResponse verify(byte[] pdfBytes) {
        Objects.requireNonNull(pdfBytes, "Dữ liệu file PDF không được null");
        Crypto.registerBouncyCastle();

        long fileSize = pdfBytes.length;
        List<SignatureItemResponse> itemResults = new ArrayList<>();
        List<String> warningsOrErrors = new ArrayList<>();

        if (fileSize == 0) {
            return DocumentSignatureVerifyResponse.builder()
                    .overallStatus("INVALID")
                    .overallStatusDesc("KHÔNG HỢP LỆ")
                    .totalSignatures(0)
                    .signatures(Collections.emptyList())
                    .warningsOrErrors(List.of("File PDF rỗng (0 bytes)"))
                    .summaryMessage("File PDF rỗng không thể kiểm tra chữ ký")
                    .build();
        }

        // 1. Nạp chứng thư Root CA nội bộ
        X509Certificate caCert = loadCaCertificate();

        // 2. Nạp danh sách chứng thư bị thu hồi (CRL)
        Set<String> revokedSerials = loadRevokedSerials();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            List<PDSignature> signatures = document.getSignatureDictionaries();

            if (signatures.isEmpty()) {
                return DocumentSignatureVerifyResponse.builder()
                        .overallStatus("INVALID")
                        .overallStatusDesc("KHÔNG HỢP LỆ")
                        .totalSignatures(0)
                        .signatures(Collections.emptyList())
                        .warningsOrErrors(List.of("Tài liệu không chứa bất kỳ chữ ký số nào"))
                        .summaryMessage("Tài liệu chưa được ký số")
                        .build();
            }

            // Sắp xếp các chữ ký theo đúng thứ tự vùng bao phủ ByteRange
            List<PDSignature> sortedSignatures = new ArrayList<>(signatures);
            sortedSignatures.sort(Comparator.comparingInt(s -> {
                int[] br = s.getByteRange();
                return (br != null && br.length == 4) ? (br[2] + br[3]) : 0;
            }));

            int totalSigCount = sortedSignatures.size();

            for (int i = 0; i < totalSigCount; i++) {
                PDSignature sig = sortedSignatures.get(i);
                SignatureItemResponse item = new SignatureItemResponse();
                item.setIndex(i + 1);

                // Tìm tên field tương ứng trong AcroForm
                item.setFieldName(findFieldNameForSignature(document, sig));

                // Trích xuất ByteRange
                int[] byteRange = sig.getByteRange();
                if (byteRange == null || byteRange.length != 4) {
                    item.setIntegrityPass(false);
                    item.setIntegrityDetails("FAIL (ByteRange không hợp lệ hoặc thiếu thông số)");
                    warningsOrErrors.add("Chữ ký #" + item.getIndex() + " (" + item.getFieldName() + "): ByteRange lỗi");
                    itemResults.add(item);
                    continue;
                }

                int start1 = byteRange[0];
                int len1 = byteRange[1];
                int start2 = byteRange[2];
                int len2 = byteRange[3];
                int signedLength = len1 + len2;

                // Kiểm tra phạm vi ByteRange có nằm trong file không
                if (start1 < 0 || len1 < 0 || start2 < 0 || len2 < 0
                        || start1 + len1 > fileSize || start2 + len2 > fileSize) {
                    item.setIntegrityPass(false);
                    item.setIntegrityDetails("FAIL (ByteRange vượt quá kích thước file)");
                    warningsOrErrors.add("Chữ ký #" + item.getIndex() + " (" + item.getFieldName() + "): Vượt kích thước file");
                    itemResults.add(item);
                    continue;
                }

                // Trích xuất vùng byte cần ký
                byte[] signedContent = new byte[signedLength];
                System.arraycopy(pdfBytes, start1, signedContent, 0, len1);
                System.arraycopy(pdfBytes, start2, signedContent, len1, len2);

                // Trích xuất nội dung chữ ký /Contents từ PDF
                byte[] cmsContents = null;
                try {
                    cmsContents = sig.getContents(pdfBytes);
                } catch (Exception ignored) {
                }
                if (cmsContents == null || cmsContents.length == 0) {
                    try {
                        cmsContents = sig.getContents();
                    } catch (Exception ignored) {
                    }
                }

                // Trích xuất chứng thư người ký từ khối CMS
                X509Certificate signerCert = extractSignerCertificate(cmsContents);
                if (signerCert != null) {
                    item.setSignerName(extractSignerCN(signerCert));
                    item.setSignerTitle(extractSignerTitle(signerCert));
                    item.setOrganizationUnit(extractSignerOU(signerCert));
                    item.setUserId(extractSignerUID(signerCert));
                    item.setSerialHex(signerCert.getSerialNumber().toString(16).toLowerCase());

                    // 2. TIÊU CHÍ 2: CHAIN (Xác thực với Root CA)
                    checkChain(item, signerCert, caCert);

                    // 3. TIÊU CHÍ 3: VALIDITY (Thời hạn hiệu lực)
                    checkValidity(item, signerCert, sig.getSignDate());

                    // 4. TIÊU CHÍ 4: KEYUSAGE & BASIC CONSTRAINTS
                    checkKeyUsage(item, signerCert);

                    // 5. TIÊU CHÍ 5: REVOCATION (Kiểm tra thu hồi CRL)
                    checkRevocation(item, signerCert, revokedSerials);

                    // 7. TIÊU CHÍ 7: IDENTITY (Đối chiếu UID với tên field)
                    checkIdentity(item);
                } else {
                    item.setChainPass(false);
                    item.setChainDetails("FAIL (Không trích xuất được chứng thư số từ CMS /Contents)");
                    item.setValidityPass(false);
                    item.setValidityDetails("FAIL (Không có chứng thư để kiểm tra thời hạn)");
                    item.setKeyUsagePass(false);
                    item.setKeyUsageDetails("FAIL (Không có chứng thư để kiểm tra KeyUsage)");
                    item.setRevocationPass(false);
                    item.setRevocationDetails("FAIL (Không có chứng thư để kiểm tra thu hồi)");
                    item.setIdentityPass(false);
                    item.setIdentityDetails("FAIL (Không có chứng thư để đối chiếu danh tính)");
                }

                // 1. TIÊU CHÍ 1: INTEGRITY (Toàn vẹn băm ByteRange & Chữ ký CMS)
                checkIntegrity(item, signedContent, cmsContents, signerCert);

                // 6. TIÊU CHÍ 6: COVERAGE (Độ phủ ByteRange & Incremental Update)
                checkCoverage(item, byteRange, fileSize, i == totalSigCount - 1);

                // Đánh giá đạt chuẩn toàn diện
                boolean fullyValid = item.isIntegrityPass() && item.isChainPass() && item.isValidityPass()
                        && item.isKeyUsagePass() && item.isRevocationPass() && item.isCoveragePass() && item.isIdentityPass();
                item.setFullyValid(fullyValid);

                boolean hasFatal = !item.isIntegrityPass() || !item.isChainPass() || !item.isRevocationPass()
                        || !item.isIdentityPass() || !item.isValidityPass();

                if (hasFatal) {
                    warningsOrErrors.add("LỖI NGHIÊM TRỌNG tại Chữ ký #" + item.getIndex()
                            + " (" + item.getSignerName() + ", Field: " + item.getFieldName() + ")");
                } else if (!fullyValid) {
                    warningsOrErrors.add("CẢNH BÁO tại Chữ ký #" + item.getIndex()
                            + " (" + item.getSignerName() + "): " + item.getCoverageDetails());
                }

                itemResults.add(item);
            }

            // Đánh giá trạng thái tổng thể
            String overallStatus = "VALID";
            String overallDesc = "HỢP LỆ (100% TIÊU CHÍ AN TOÀN)";
            boolean hasWarning = false;

            for (SignatureItemResponse item : itemResults) {
                boolean hasFatal = !item.isIntegrityPass() || !item.isChainPass() || !item.isRevocationPass()
                        || !item.isIdentityPass() || !item.isValidityPass();
                if (hasFatal) {
                    overallStatus = "INVALID";
                    overallDesc = "KHÔNG HỢP LỆ";
                    break;
                }
                if (!item.isFullyValid()) {
                    hasWarning = true;
                }
            }

            if (!"INVALID".equals(overallStatus) && hasWarning) {
                overallStatus = "WARNING";
                overallDesc = "CÓ CẢNH BÁO";
            }

            String summary = String.format("Tổng số chữ ký: %d. Trạng thái: %s. Chữ ký lỗi/cảnh báo: %d",
                    itemResults.size(), overallDesc, warningsOrErrors.size());

            return DocumentSignatureVerifyResponse.builder()
                    .overallStatus(overallStatus)
                    .overallStatusDesc(overallDesc)
                    .totalSignatures(itemResults.size())
                    .signatures(itemResults)
                    .warningsOrErrors(warningsOrErrors)
                    .summaryMessage(summary)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi trong quá trình kiểm định tài liệu PDF: {}", e.getMessage(), e);
            return DocumentSignatureVerifyResponse.builder()
                    .overallStatus("INVALID")
                    .overallStatusDesc("LỖI PHÂN TÍCH TÀI LIỆU")
                    .totalSignatures(0)
                    .signatures(Collections.emptyList())
                    .warningsOrErrors(List.of("Lỗi khi đọc file PDF: " + e.getMessage()))
                    .summaryMessage("Không thể giải mã hoặc phân tích cú pháp PDF")
                    .build();
        }
    }

    /**
     * Xác thực các chữ ký số từ đường dẫn file PDF trên ổ đĩa.
     */
    public DocumentSignatureVerifyResponse verify(Path pdfPath) {
        Objects.requireNonNull(pdfPath, "Đường dẫn file PDF không được null");
        if (!Files.exists(pdfPath)) {
            throw new IllegalArgumentException("File PDF không tồn tại: " + pdfPath.toAbsolutePath());
        }
        try {
            byte[] bytes = Files.readAllBytes(pdfPath);
            return verify(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi đọc file PDF từ ổ đĩa: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 7 TIÊU CHÍ BẢO MẬT CHI TIẾT
    // =========================================================================

    /**
     * Tiêu chí 1: Kiểm tra tính toàn vẹn (Integrity)
     */
    private void checkIntegrity(SignatureItemResponse item, byte[] signedContent, byte[] cmsContents, X509Certificate signerCert) {
        try {
            if (cmsContents == null || cmsContents.length == 0) {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Khối chữ ký /Contents rỗng)");
                return;
            }

            // 1. Tính toán SHA-256 trên nội dung thực tế theo ByteRange
            MessageDigest md = MessageDigest.getInstance("SHA-256", BouncyCastleProvider.PROVIDER_NAME);
            byte[] calculatedHash = md.digest(signedContent);

            // 2. Phân tích cú pháp CMS/PKCS#7
            ContentInfo contentInfo = parseContentInfo(cmsContents);
            if (contentInfo == null) {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Không thể đọc cấu trúc CMS/ContentInfo)");
                return;
            }
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), contentInfo);
            Collection<SignerInformation> signers = cms.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Không tìm thấy SignerInfo trong khối CMS)");
                return;
            }

            SignerInformation signerInfo = signers.iterator().next();

            // 3. So khớp messageDigest trong Signed Attributes
            Attribute mdAttr = signerInfo.getSignedAttributes().get(CMSAttributes.messageDigest);
            if (mdAttr == null) {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Thiếu thuộc tính messageDigest trong Signed Attributes)");
                return;
            }

            ASN1OctetString octets = (ASN1OctetString) mdAttr.getAttrValues().getObjectAt(0);
            byte[] embeddedHash = octets.getOctets();

            if (!Arrays.equals(calculatedHash, embeddedHash)) {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Mã băm ByteRange không khớp messageDigest: dữ liệu PDF đã bị chỉnh sửa trái phép)");
                return;
            }

            // 4. Xác minh chữ ký CMS bằng Public Key của người ký
            if (signerCert == null) {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Không tìm thấy chứng thư của signer để xác thực chữ ký CMS)");
                return;
            }

            boolean sigVerified = signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(signerCert));

            if (sigVerified) {
                item.setIntegrityPass(true);
                item.setIntegrityDetails("PASS (Mã băm toàn vẹn & chữ ký mật mã CMS hợp lệ)");
            } else {
                item.setIntegrityPass(false);
                item.setIntegrityDetails("FAIL (Chữ ký mật mã CMS không khớp khóa công khai của người ký)");
            }

        } catch (Exception e) {
            item.setIntegrityPass(false);
            item.setIntegrityDetails("FAIL (" + e.getMessage() + ")");
        }
    }

    /**
     * Tiêu chí 2: Kiểm tra chuỗi chứng thư số (Chain of Trust)
     */
    private void checkChain(SignatureItemResponse item, X509Certificate signerCert, X509Certificate caCert) {
        try {
            if (caCert == null) {
                item.setChainPass(false);
                item.setChainDetails("FAIL (Không tìm thấy Root CA nội bộ để xác minh chuỗi tin cậy)");
                return;
            }

            // Không chấp nhận chứng thư tự ký mạo danh
            if (signerCert.getSubjectX500Principal().equals(signerCert.getIssuerX500Principal())) {
                item.setChainPass(false);
                item.setChainDetails("FAIL (Chứng thư tự ký mạo danh, không qua Root CA nội bộ)");
                return;
            }

            // Kiểm tra Issuer DN phải khớp với Subject DN của Root CA
            if (!signerCert.getIssuerX500Principal().equals(caCert.getSubjectX500Principal())) {
                item.setChainPass(false);
                item.setChainDetails("FAIL (Issuer DN không khớp với Subject DN của Root CA nội bộ)");
                return;
            }

            // Xác minh chữ ký số của CA trên chứng thư người dùng
            signerCert.verify(caCert.getPublicKey());
            item.setChainPass(true);
            item.setChainDetails("OK (Được cấp phát và xác thực bởi Root CA nội bộ)");

        } catch (Exception e) {
            item.setChainPass(false);
            item.setChainDetails("FAIL (Chữ ký của CA không hợp lệ: " + e.getMessage() + ")");
        }
    }

    /**
     * Tiêu chí 3: Kiểm tra thời hạn hiệu lực (Validity)
     */
    private void checkValidity(SignatureItemResponse item, X509Certificate signerCert, java.util.Calendar signCal) {
        Date signDate = (signCal != null) ? signCal.getTime() : new Date();
        item.setSignDate(toLocalDateTime(signDate));
        Date now = new Date();

        try {
            // Kiểm tra tại thời điểm ký
            signerCert.checkValidity(signDate);

            // Kiểm tra tại thời điểm hiện tại
            try {
                signerCert.checkValidity(now);
                item.setValidityPass(true);
                item.setValidityDetails("OK (Còn hạn hiệu lực cả lúc ký và hiện tại)");
            } catch (Exception e) {
                // Hết hạn ở hiện tại nhưng còn hạn lúc ký -> Cảnh báo
                item.setValidityPass(true);
                item.setValidityDetails("CẢNH BÁO (Hết hạn ở thời điểm hiện tại, nhưng hợp lệ tại thời điểm ký)");
            }
        } catch (Exception e) {
            item.setValidityPass(false);
            item.setValidityDetails("FAIL (Chứng thư không còn hạn tại thời điểm ký: " + signDate + ")");
        }
    }

    /**
     * Tiêu chí 4: Kiểm tra KeyUsage và BasicConstraints
     */
    private void checkKeyUsage(SignatureItemResponse item, X509Certificate signerCert) {
        try {
            // BasicConstraints: CA=false (getBasicConstraints() == -1)
            int bc = signerCert.getBasicConstraints();
            if (bc != -1) {
                item.setKeyUsagePass(false);
                item.setKeyUsageDetails("FAIL (Chứng thư người ký là chứng thư CA: pathLen=" + bc + ")");
                return;
            }

            // KeyUsage: digitalSignature (bit 0) và nonRepudiation (bit 1)
            boolean[] ku = signerCert.getKeyUsage();
            if (ku == null || ku.length < 2 || !ku[0] || !ku[1]) {
                item.setKeyUsagePass(false);
                item.setKeyUsageDetails("FAIL (Thiếu quyền digitalSignature hoặc nonRepudiation)");
                return;
            }

            item.setKeyUsagePass(true);
            item.setKeyUsageDetails("OK (digitalSignature & nonRepudiation hợp lệ)");
        } catch (Exception e) {
            item.setKeyUsagePass(false);
            item.setKeyUsageDetails("FAIL (" + e.getMessage() + ")");
        }
    }

    /**
     * Tiêu chí 5: Kiểm tra danh sách thu hồi (Revocation)
     */
    private void checkRevocation(SignatureItemResponse item, X509Certificate signerCert, Set<String> revokedSerials) {
        try {
            String serialHex = signerCert.getSerialNumber().toString(16).toLowerCase();

            if (revokedSerials.contains(serialHex)) {
                item.setRevocationPass(false);
                item.setRevocationDetails("REVOKED (Serial 0x" + serialHex.toUpperCase() + " đã bị thu hồi trong danh sách CRL)");
            } else {
                item.setRevocationPass(true);
                item.setRevocationDetails("OK (ACTIVE - Chứng thư còn hiệu lực, chưa bị thu hồi)");
            }
        } catch (Exception e) {
            item.setRevocationPass(false);
            item.setRevocationDetails("FAIL (" + e.getMessage() + ")");
        }
    }

    /**
     * Tiêu chí 6: Kiểm tra độ phủ ByteRange (Coverage)
     */
    private void checkCoverage(SignatureItemResponse item, int[] byteRange, long fileSize, boolean isLastSignature) {
        int endOfByteRange = byteRange[2] + byteRange[3];

        if (isLastSignature) {
            if (endOfByteRange == fileSize) {
                item.setCoveragePass(true);
                item.setCoverageDetails(String.format("FULL_FILE (%d KB - 100%% tệp được bảo vệ)", fileSize / 1024));
            } else if (endOfByteRange < fileSize) {
                long appendedBytes = fileSize - endOfByteRange;
                item.setCoveragePass(false);
                item.setCoverageDetails(String.format("CẢNH BÁO (Có %d bytes bị nối thêm sau khi ký)", appendedBytes));
            } else {
                item.setCoveragePass(false);
                item.setCoverageDetails("FAIL (ByteRange vượt quá kích thước file)");
            }
        } else {
            // Chữ ký trước: ByteRange là tiền tố hợp lệ trong chuỗi incremental updates
            if (endOfByteRange <= fileSize) {
                item.setCoveragePass(true);
                item.setCoverageDetails(String.format("INCREMENTAL (v%d - Cập nhật tăng dần bảo toàn)", item.getIndex()));
            } else {
                item.setCoveragePass(false);
                item.setCoverageDetails("FAIL (ByteRange lỗi)");
            }
        }
    }

    /**
     * Tiêu chí 7: Đối chiếu danh tính (Identity Matching)
     */
    private void checkIdentity(SignatureItemResponse item) {
        try {
            if (item.getUserId() == null || item.getUserId().isBlank()) {
                item.setIdentityPass(false);
                item.setIdentityDetails("FAIL (Không tìm thấy thuộc tính UID trong chứng thư số)");
                return;
            }

            String uid = item.getUserId();
            String field = item.getFieldName();
            boolean match = field != null && (
                    field.equalsIgnoreCase("sig_user_" + uid) ||
                    field.equalsIgnoreCase("sig_approve_" + uid) ||
                    field.equalsIgnoreCase("sig_publish_" + uid) ||
                    field.equalsIgnoreCase("sig_certify_" + uid) ||
                    field.equalsIgnoreCase("sig_official_" + uid)
            );
            if (match) {
                item.setIdentityPass(true);
                item.setIdentityDetails("MATCH (Khớp định danh User ID: " + uid + ")");
            } else {
                item.setIdentityPass(false);
                item.setIdentityDetails("MISMATCH (Field: " + field + ", UID: " + uid + ")");
            }
        } catch (Exception e) {
            item.setIdentityPass(false);
            item.setIdentityDetails("FAIL (" + e.getMessage() + ")");
        }
    }

    // =========================================================================
    // CÁC HÀM TIỆN ÍCH TRÍCH XUẤT X.509
    // =========================================================================

    private X509Certificate loadCaCertificate() {
        try {
            Path caPath = Paths.get(caCertPath);
            if (Files.exists(caPath)) {
                return Crypto.loadCertPem(caPath);
            }
            log.warn("Không tìm thấy tệp Root CA tại '{}'", caPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Lỗi khi nạp chứng thư Root CA từ '{}': {}", caCertPath, e.getMessage());
        }
        return null;
    }

    private Set<String> loadRevokedSerials() {
        try {
            Path path = Paths.get(revokedPath);
            if (!Files.exists(path)) {
                return Collections.emptySet();
            }
            ObjectMapper mapper = (this.objectMapper != null) ? this.objectMapper : new ObjectMapper();
            List<String> list = mapper.readValue(path.toFile(), new TypeReference<List<String>>() {});
            if (list == null) return Collections.emptySet();

            Set<String> set = new HashSet<>();
            for (String s : list) {
                if (s != null && !s.isBlank()) {
                    set.add(s.trim().toLowerCase());
                }
            }
            return set;
        } catch (Exception e) {
            log.warn("Không thể nạp danh sách thu hồi CRL từ '{}': {}", revokedPath, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Thêm số serial của chứng thư vào danh sách thu hồi CRL (revoked.json).
     */
    public synchronized void revokeSerial(String serialHex) {
        if (serialHex == null || serialHex.isBlank()) {
            return;
        }
        revokeSerials(List.of(serialHex));
    }

    /**
     * Thêm tập hợp các số serial chứng thư vào danh sách thu hồi CRL (revoked.json).
     */
    public synchronized void revokeSerials(Collection<String> serialHexes) {
        if (serialHexes == null || serialHexes.isEmpty()) {
            return;
        }

        try {
            Path path = Paths.get(revokedPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            ObjectMapper mapper = (this.objectMapper != null) ? this.objectMapper : new ObjectMapper();
            Set<String> set = new LinkedHashSet<>(loadRevokedSerials());
            for (String s : serialHexes) {
                if (s != null && !s.isBlank()) {
                    set.add(s.trim().toLowerCase());
                }
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), set);
            log.info("Đã cập nhật danh sách thu hồi CRL ({}) với các serials: {}", path, serialHexes);
        } catch (Exception e) {
            log.error("Lỗi khi ghi danh sách thu hồi CRL vào '{}': {}", revokedPath, e.getMessage(), e);
            throw new RuntimeException("Không thể cập nhật danh sách thu hồi CRL: " + e.getMessage(), e);
        }
    }

    private String findFieldNameForSignature(PDDocument document, PDSignature signature) {
        try {
            for (PDSignatureField field : document.getSignatureFields()) {
                if (field.getSignature() != null && field.getSignature().getCOSObject() == signature.getCOSObject()) {
                    String partial = field.getPartialName();
                    if (partial != null && !partial.isBlank()) {
                        return partial;
                    }
                    return field.getFullyQualifiedName();
                }
            }
        } catch (Exception ignored) {
        }
        return "unnamed_sig";
    }

    private ContentInfo parseContentInfo(byte[] rawCmsBytes) {
        if (rawCmsBytes == null || rawCmsBytes.length == 0) return null;
        try (ASN1InputStream asn1In = new ASN1InputStream(new ByteArrayInputStream(rawCmsBytes))) {
            ASN1Primitive primitive = asn1In.readObject();
            if (primitive != null) {
                return ContentInfo.getInstance(primitive);
            }
        } catch (Exception e) {
            log.warn("Không thể phân tích cấu trúc ASN.1/ContentInfo: {}", e.getMessage());
        }
        return null;
    }

    private X509Certificate extractSignerCertificate(byte[] cmsContents) {
        if (cmsContents == null || cmsContents.length == 0) return null;
        try {
            ContentInfo contentInfo = parseContentInfo(cmsContents);
            if (contentInfo == null) return null;

            CMSSignedData cms = new CMSSignedData(contentInfo);
            SignerInformation signerInfo = cms.getSignerInfos().getSigners().iterator().next();
            Store<X509CertificateHolder> certStore = cms.getCertificates();
            Collection<X509CertificateHolder> matches = certStore.getMatches(signerInfo.getSID());

            if (!matches.isEmpty()) {
                X509CertificateHolder holder = matches.iterator().next();
                return new JcaX509CertificateConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .getCertificate(holder);
            }
        } catch (Exception e) {
            log.warn("Không thể trích xuất chứng thư từ khối CMS: {}", e.getMessage(), e);
        }
        return null;
    }

    private String extractSignerCN(X509Certificate cert) {
        String cn = extractAttr(cert, BCStyle.CN);
        return (cn != null && !cn.isBlank()) ? cn : cert.getSubjectX500Principal().getName();
    }

    private String extractSignerTitle(X509Certificate cert) {
        return extractAttr(cert, BCStyle.T);
    }

    private String extractSignerOU(X509Certificate cert) {
        return extractAttr(cert, BCStyle.OU);
    }

    private String extractSignerUID(X509Certificate cert) {
        return extractAttr(cert, BCStyle.UID);
    }

    private String extractAttr(X509Certificate cert, ASN1ObjectIdentifier oid) {
        try {
            X500Name x500 = new JcaX509CertificateHolder(cert).getSubject();
            RDN[] rdns = x500.getRDNs(oid);
            if (rdns != null && rdns.length > 0) {
                return IETFUtils.valueToString(rdns[0].getFirst().getValue());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}

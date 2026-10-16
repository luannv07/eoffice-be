package vn.edu.epu.eoffice_be.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.RefreshToken;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionDegRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.RefreshTokenRepository;
import vn.edu.epu.eoffice_be.repository.RolePermissionRepository;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.Crypto;
import vn.edu.epu.eoffice_be.security.pki.PdfSignatureVerifier;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.FileSecurityValidator;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage kiểm thử mức đơn vị cho 5 kịch bản tấn công runtime (Attack Vectors)
 * mô tả tại Bảng 4 — Chương 3 tài liệu nâng cấp Scopus Q2
 * ({@code DevSecOps_EOffice_Scopus_Q2_Upgrade.docx}).
 *
 * <p>Mỗi nhóm test ánh xạ 1-1 tới một kịch bản DAST để pipeline 4
 * (OWASP ZAP) có oracle kỳ vọng ở tầng service khi fuzz endpoint thật:
 *
 * <ul>
 *   <li>Vector 1 — Broken State Machine &amp; BFLA (A01)</li>
 *   <li>Vector 2 — JWT Token Replay &amp; Revocation (A07)</li>
 *   <li>Vector 3 — PDF Magic Bytes &amp; Exploit Bypass (A03)</li>
 *   <li>Vector 4 — Delegation Scope Manipulation (A01)</li>
 *   <li>Vector 5 — CMS Signature Verifier Fuzzing (A08)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DAST Runtime Attack Vectors — Service-level Coverage (Scopus Q2 Table 4)")
class DastRuntimeSecurityCoverageTest {

    // ---- Vector 1 & anchors: WorkflowEngine (BFLA / state machine) ----
    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentWorkflowLogRepository documentWorkflowLogRepository;

    @InjectMocks
    private WorkflowEngine workflowEngine;

    // ---- Vector 2: JWT replay (RefreshTokenService) ----
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    // ---- Vector 4: delegation scope (PermissionResolver) ----
    @Mock
    private PositionAssignmentRepository positionAssignmentRepository;

    @Mock
    private PositionPermissionRepository positionPermissionRepository;

    @Mock
    private PositionDegRepository positionDegRepository;

    @Mock
    private OrgNodeRepository orgNodeRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionResolver permissionResolver;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupCrypto() {
        Crypto.registerBouncyCastle();
    }

    // ================= Vector 1: Broken State Machine & BFLA (A01) =================

    @Test
    @DisplayName("Vector 1 — IN_REVIEW + SIGN_AND_PUBLISH (nhảy cóc qua lãnh đạo) phải bị chặn")
    void bfla_skipReviewToPublish_shouldThrowTransitionNotAllowed() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.getNextStatus(DocumentStatus.IN_REVIEW, WorkflowAction.SIGN_AND_PUBLISH));
        assertEquals(ErrorCode.WORKFLOW_TRANSITION_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Vector 1 — PUBLISHED + SUBMIT (tái ban hành văn bản đã phát hành) phải bị chặn")
    void bfla_republishPublished_shouldThrowTransitionNotAllowed() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.getNextStatus(DocumentStatus.PUBLISHED, WorkflowAction.SUBMIT));
        assertEquals(ErrorCode.WORKFLOW_TRANSITION_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Vector 1 — REVOKE không kèm lý do phải bị chặn với BAD_REQUEST")
    void bfla_revokeWithoutReason_shouldThrowBadRequest() {
        Document doc = Document.builder().id(1L).status(DocumentStatus.PUBLISHED).build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.validateTransition(doc, WorkflowAction.REVOKE, "   "));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Vector 1 — transition trên văn bản null phải báo DOCUMENT_NOT_FOUND")
    void bfla_nullDocument_shouldThrowNotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.validateTransition(null, WorkflowAction.SUBMIT, "Gui duyet"));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    // ================= Vector 2: JWT Token Replay & Revocation (A07) =================

    @Test
    @DisplayName("Vector 2 — tái sử dụng refresh token đã xoay chuyển phải thu hồi cả họ token")
    void jwtReplay_reuseRotatedToken_shouldRevokeFamilyAndThrowSessionExpired() {
        RefreshToken old = RefreshToken.builder()
                .userId(10L)
                .rt("old-jti")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByRt("old-jti")).thenReturn(Optional.of(old));

        // Lượt xoay chuyển hợp lệ lần 1
        refreshTokenService.rotate(10L, "old-jti", "new-jti", Instant.now().plusSeconds(7200));
        assertTrue(old.isRevoked());

        // Kẻ tấn công replay lại token cũ đã bị thu hồi
        BusinessException ex = assertThrows(BusinessException.class, () ->
                refreshTokenService.rotate(10L, "old-jti", "newer-jti", Instant.now().plusSeconds(7200)));

        assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
        verify(refreshTokenRepository).revokeAllActiveByUserId(10L);
    }

    // ================= Vector 3: PDF Magic Bytes & Exploit Bypass (A03) =================

    private FileSecurityValidator newValidator() {
        UploadProperties properties = new UploadProperties();
        properties.setMaxSizeBytes(10 * 1024 * 1024);
        properties.setAllowedExtensions(List.of("docx", "pdf", "DOCX", "PDF"));
        return new FileSecurityValidator(properties);
    }

    @Test
    @DisplayName("Vector 3 — PDF gắn /Launch độc hại (dù đủ magic bytes) phải bị chặn FILE_UNSAFE")
    void uploadMalware_pdfWithLaunchAction_shouldThrowFileUnsafe() {
        String payload = "%PDF-1.7\n1 0 obj\n<< /Type /Action /S /Launch /Win (cmd.exe) >>\n% malware.exe stub";
        MockMultipartFile file = new MockMultipartFile(
                "file", "quyetdinh.pdf", "application/pdf",
                payload.getBytes(StandardCharsets.ISO_8859_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newValidator().validateFile(file));
        assertEquals(ErrorCode.FILE_UNSAFE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Vector 3 — DOCX chứa file thực thi (.exe) phải bị chặn FILE_UNSAFE")
    void uploadMalware_docxWithExecutableEntry_shouldThrowFileUnsafe() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("payload.exe"));
            zos.write(new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00});
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "baocao.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newValidator().validateFile(file));
        assertEquals(ErrorCode.FILE_UNSAFE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Vector 3 — ZIP giả DOCX thiếu [Content_Types].xml phải bị chặn FILE_INVALID_FORMAT")
    void uploadMalware_docxWithoutContentTypes_shouldThrowInvalidFormat() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "gia.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newValidator().validateFile(file));
        assertEquals(ErrorCode.FILE_INVALID_FORMAT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Vector 3 — tên file dùng backslash traversal (..\\..) phải bị chặn FILE_UNSAFE")
    void uploadMalware_backslashTraversalFilename_shouldThrowFileUnsafe() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "..\\..\\secret.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newValidator().validateFile(file));
        assertEquals(ErrorCode.FILE_UNSAFE, ex.getErrorCode());
    }

    // ================= Vector 4: Delegation Scope Manipulation (A01) =================

    @Test
    @DisplayName("Vector 4 — ủy quyền hết hiệu lực (ngoài khoảng effective) không được cấp quyền")
    void delegation_expiredInterval_shouldReturnFalse() {
        JwtUserPrincipal principal = new JwtUserPrincipal(10L, "testuser", "USER", true, Collections.emptyList());
        when(positionAssignmentRepository.findActiveAssignments(eq(10L), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        // Mô phỏng truy vấn hasValidDelegation: effectiveFrom/effectiveTo không còn bao phủ hiện tại
        when(positionDegRepository.hasValidDelegation(eq(10L), eq(200L), eq("doc:create"), any(Instant.class)))
                .thenReturn(false);

        assertFalse(permissionResolver.hasPermission(principal, "doc:create", 200L));
    }

    @Test
    @DisplayName("Vector 4 — orgNodeId null thì bỏ qua kiểm tra delegation (không chạm position_degs)")
    void delegation_nullOrgNode_shouldSkipDelegationCheck() {
        JwtUserPrincipal principal = new JwtUserPrincipal(10L, "testuser", "USER", true, Collections.emptyList());
        when(positionAssignmentRepository.findActiveAssignments(eq(10L), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        assertFalse(permissionResolver.hasPermission(principal, "doc:create", null));
        verify(positionDegRepository, never())
                .hasValidDelegation(any(), any(), any(), any());
    }

    // ================= Vector 5: CMS Signature Verifier Fuzzing (A08) =================

    private byte[] createSamplePdfBytes() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 720);
                cs.showText("VAN BAN THU NGHIEM FUZZING ByteRange");
                cs.endText();
            }
            document.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Vector 5 — PDF đã ký bị sửa 1 byte trong vùng ByteRange phải cho kết quả INVALID")
    void cmsVerifier_tamperedByteRange_shouldReturnInvalid() throws Exception {
        // Điều kiện môi trường: cần Root CA (scripts/init-ca.*) như CI chạy trước mvn verify.
        // Thiếu CA thì skip thay vì error — cùng precondition với PdfSignerTest/PdfSignatureVerifierTest.
        assumeTrue(Files.exists(Paths.get("./uploads/keystores/ca/ca.p12")),
                "Bỏ qua: chưa khởi tạo PKI Root CA (chạy scripts/init-ca.ps1 trước).");
        byte[] basePdf = createSamplePdfBytes();
        PdfSigner signer = new PdfSigner(tempDir.resolve("keystores_fuzz").toString(), "changeit");
        byte[] signed = signer.signBytes(basePdf, "1001", "Nguyen Van A - Chuyen vien CNTT", "Ký nháy")
                .signedPdfBytes();
        assertNotNull(signed);

        // Fuzz: lật 1 byte trong vùng nội dung đã ký (đầu file, chắc chắn thuộc ByteRange)
        byte[] tampered = Arrays.copyOf(signed, signed.length);
        tampered[64] ^= (byte) 0xFF;

        PdfSignatureVerifier verifier = new PdfSignatureVerifier(
                "./uploads/keystores/ca/ca.crt", tempDir.resolve("revoked_fuzz.json").toString());
        DocumentSignatureVerifyResponse res = verifier.verify(tampered);

        assertNotNull(res);
        assertEquals("INVALID", res.getOverallStatus());
        assertTrue(res.getSignatures().isEmpty() || !res.getSignatures().get(0).isIntegrityPass(),
                "Kỳ vọng: parse lỗi (rỗng) hoặc INTEGRITY_FAILED khi ByteRange bị sửa");
    }
}

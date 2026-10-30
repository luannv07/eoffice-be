package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.DocumentAttachmentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentVersionRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.storage.FileStorageService;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.storage.dto.StoredFileInfo;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAttachmentService {

    private final DocumentRepository documentRepository;
    private final DocumentReadPolicy readPolicy;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentProcessLogRepository documentProcessLogRepository;
    private final UserLoginRepository userLoginRepository;
    private final FileStorageService fileStorageService;
    private final DocumentApprovalRequestRepository documentApprovalRequestRepository;
    private final DocumentPdfStorageService documentPdfStorageService;

    @Transactional(readOnly = true)
    public Resource downloadPrimaryPdf(Long documentId, Long userId) {
        return downloadDocumentPdf(documentId, userId, false);
    }

    @Transactional(readOnly = true)
    public Resource downloadOfficialPdf(Long documentId, Long userId) {
        return downloadDocumentPdf(documentId, userId, true);
    }

    private Resource downloadDocumentPdf(Long documentId, Long userId, boolean official) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        assertCanAccessAttachment(document, userId);
        // Each URL selects exactly one version; storage reports missing/invalid references.
        String reference = official ? document.getOfficialFilePath() : document.getPrimaryFilePath();
        return documentPdfStorageService.readAsResource(reference);
    }

    /**
     * UC09: Tải tệp PDF đính kèm lên hệ thống
     */
    @Transactional
    public DocumentAttachmentResponse uploadAttachment(Long documentId, MultipartFile file, boolean isMain, Long userId) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền: chỉ người tạo hoặc admin mới được upload tệp
        if (readPolicy.applies(document) || DocumentAdminPolicy.isAdmin(user)) readPolicy.assertDraftWrite(document, userId);
        UserLogin drafter = document.getDrafter();
        boolean isDrafter = drafter != null && drafter.getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!isDrafter && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền tải tệp lên dự thảo này");
        }

        // Kiểm tra trạng thái: chỉ cho phép upload khi là DRAFT hoặc REJECTED
        if (document.getStatus() != DocumentStatus.DRAFT 
        && document.getStatus() != DocumentStatus.REJECTED
        && document.getStatus() != DocumentStatus.NEEDS_REVISION) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Chỉ có thể tải tệp lên khi văn bản ở trạng thái Dự thảo hoặc Cần chỉnh sửa");
        }

        // Chỉ giới hạn định dạng tại luồng văn bản; giữ validator/storage dùng chung.
        if (file != null && !file.isEmpty()) {
            String filename = file.getOriginalFilename();
            if (filename != null && !filename.isBlank()) {
                int extensionIndex = filename.lastIndexOf('.');
                if (extensionIndex < 0 || !"pdf".equalsIgnoreCase(filename.substring(extensionIndex + 1))) {
                    throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Chỉ chấp nhận tệp PDF");
                }
            }
        }

        // Lưu trữ tệp và quét an toàn
        StoredFileInfo storedInfo = fileStorageService.storeFile(file, documentId);

        // Nếu đây là tệp chính, hủy cờ tệp chính của các tệp cũ
        if (isMain) {
            List<DocumentAttachment> oldMains = documentAttachmentRepository
                    .findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(documentId);
            for (DocumentAttachment oldMain : oldMains) {
                oldMain.setMainDocument(false);
                documentAttachmentRepository.save(oldMain);
            }
        }

        // Lấy phiên bản hiện tại để liên kết
        Optional<DocumentVersion> currentVersion = documentVersionRepository
                .findTopByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(documentId);

        DocumentAttachment attachment = DocumentAttachment.builder()
                .document(document)
                .documentVersion(currentVersion.orElse(null))
                .originalFileName(storedInfo.getOriginalFileName())
                .storageFileName(storedInfo.getStorageFileName())
                .storagePath(storedInfo.getStoragePath())
                .fileExtension(storedInfo.getFileExtension())
                .mimeType(storedInfo.getMimeType())
                .fileSizeBytes(storedInfo.getFileSizeBytes())
                .sha256Checksum(storedInfo.getSha256Checksum())
                .isMainDocument(isMain)
                .uploadedByUser(user)
                .build();

        DocumentAttachment saved = documentAttachmentRepository.save(attachment);

        // Ghi nhật ký tiến trình
        DocumentProcessLog logEntry = DocumentProcessLog.builder()
                .document(document)
                .actorUser(user)
                .action(DocumentProcessAction.UPLOAD_ATTACHMENT)
                .fromStatus(document.getStatus().name())
                .toStatus(document.getStatus().name())
                .note("Đính kèm tệp " + (isMain ? "chính: " : "phụ lục: ") + saved.getOriginalFileName())
                .build();
        documentProcessLogRepository.save(logEntry);

        log.info("Đã tải tệp lên thành công: docId={}, attachmentId={}, file={}",
                documentId, saved.getId(), saved.getOriginalFileName());

        return mapToResponse(saved);
    }

    /**
     * Tải xuống hoặc xem tệp đính kèm (có kiểm tra quyền người dùng)
     */
    @Transactional(readOnly = true)
    public Resource downloadAttachment(Long documentId, Long attachmentId, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        assertCanAccessAttachment(document, userId);

        DocumentAttachment attachment = documentAttachmentRepository
                .findByIdAndDocumentIdAndDeleteFlgFalse(attachmentId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND));

        return fileStorageService.loadAsResource(attachment.getStoragePath(), attachment.getStorageFileName());
    }

    /**
     * Lấy metadata tệp đính kèm (có kiểm tra quyền người dùng)
     */
    @Transactional(readOnly = true)
    public DocumentAttachment getAttachmentMetadata(Long documentId, Long attachmentId, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        assertCanAccessAttachment(document, userId);

        return documentAttachmentRepository
                .findByIdAndDocumentIdAndDeleteFlgFalse(attachmentId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND));
    }

    /**
     * Draft states use the participant policy. Legacy rules below apply only outside that scope:
     * - Người tạo văn bản
     * - Quản trị viên hệ thống (ADMIN, SYSTEM_ADMIN)
     * - Văn bản đã ban hành hoặc đã được phê duyệt (ISSUED, APPROVED)
     * - Người duyệt được phân công trong yêu cầu phê duyệt đang chờ xử lý (PENDING)
     */
    void assertCanAccessAttachment(Document document, Long userId) {
        if (readPolicy.applies(document)) {
            readPolicy.assertRead(document, userId);
            return;
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập để truy cập tệp đính kèm");
        }

        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (DocumentAdminPolicy.isAdmin(user)) {
            readPolicy.assertRead(document, userId);
            return;
        }
        boolean isDrafter = document.getDrafter() != null && document.getDrafter().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        boolean isPublished = document.getStatus() == DocumentStatus.PUBLISHED || document.getStatus() == DocumentStatus.APPROVED;

        boolean isApprover = documentApprovalRequestRepository
                .findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(document.getId()).stream()
                .filter(req -> req.getStatus() == ApprovalRequestStatus.PENDING)
                .anyMatch(req -> req.getApproverUser() != null && req.getApproverUser().getId().equals(userId));

        if (!isDrafter && !isAdmin && !isPublished && !isApprover) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập tệp đính kèm của văn bản này");
        }
    }

    /**
     * Xóa tệp đính kèm khỏi dự thảo
     */
    @Transactional
    public void deleteAttachment(Long documentId, Long attachmentId, Long userId) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (readPolicy.applies(document) || DocumentAdminPolicy.isAdmin(user)) readPolicy.assertDraftWrite(document, userId);
        UserLogin drafter = document.getDrafter();
        boolean isDrafter = drafter != null && drafter.getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!isDrafter && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa tệp đính kèm này");
        }

        if (document.getStatus() != DocumentStatus.DRAFT 
        && document.getStatus() != DocumentStatus.REJECTED
        && document.getStatus() != DocumentStatus.NEEDS_REVISION) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Không thể xóa tệp của văn bản đã gửi duyệt hoặc đã ban hành");
        }

        DocumentAttachment attachment = documentAttachmentRepository
                .findByIdAndDocumentIdAndDeleteFlgFalse(attachmentId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_ATTACHMENT_NOT_FOUND));

        attachment.setDeleteFlg(true);
        documentAttachmentRepository.save(attachment);

        // Ghi nhật ký tiến trình
        DocumentProcessLog logEntry = DocumentProcessLog.builder()
                .document(document)
                .actorUser(user)
                .action(DocumentProcessAction.DELETE_ATTACHMENT)
                .fromStatus(document.getStatus().name())
                .toStatus(document.getStatus().name())
                .note("Xóa tệp đính kèm: " + attachment.getOriginalFileName())
                .build();
        documentProcessLogRepository.save(logEntry);

        log.info("Đã xóa tệp đính kèm: docId={}, attachmentId={}", documentId, attachmentId);
    }

    private DocumentAttachmentResponse mapToResponse(DocumentAttachment att) {
        return DocumentAttachmentResponse.builder()
                .id(att.getId())
                .documentId(att.getDocument().getId())
                .documentVersionId(att.getDocumentVersion() != null ? att.getDocumentVersion().getId() : null)
                .originalFileName(att.getOriginalFileName())
                .fileExtension(att.getFileExtension())
                .mimeType(att.getMimeType())
                .fileSizeBytes(att.getFileSizeBytes())
                .sha256Checksum(att.getSha256Checksum())
                .isMainDocument(att.isMainDocument())
                .uploadedByUserId(att.getUploadedByUser().getId())
                .uploadedByUsername(att.getUploadedByUser().getUsername())
                .createdDate(att.getCreatedDate())
                .build();
    }
}

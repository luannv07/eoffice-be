package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentVersionResponse;
import vn.edu.epu.eoffice_be.dto.response.VersionCompareResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentVersionRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private final DocumentRepository documentRepository;
    private final DocumentReadPolicy readPolicy;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentProcessLogRepository documentProcessLogRepository;
    private final UserLoginRepository userLoginRepository;
    private final DocumentDraftService documentDraftService;

    /**
     * Xác thực quyền truy cập thông tin phiên bản văn bản.
     * Draft states use the participant policy; other states retain the legacy owner/admin rule.
     */
    UserLogin assertCanViewDocumentVersions(Document document, Long userId) {
        if (readPolicy.applies(document)) {
            readPolicy.assertRead(document, userId);
            return readPolicy.requireUser(userId);
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập để truy cập thông tin phiên bản văn bản");
        }

        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        boolean isDrafter = document.getDrafter() != null && document.getDrafter().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!isDrafter && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập hoặc thao tác trên phiên bản của văn bản này");
        }

        return user;
    }

    /**
     * Lấy danh sách lịch sử tất cả phiên bản của văn bản (có kiểm tra quyền)
     */
    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getVersions(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        assertCanViewDocumentVersions(document, userId);

        List<DocumentVersion> versions = documentVersionRepository
                .findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(documentId);

        return versions.stream().map(this::mapToVersionResponse).toList();
    }

    /**
     * So sánh khác biệt giữa 2 phiên bản văn bản (có kiểm tra quyền)
     */
    @Transactional(readOnly = true)
    public VersionCompareResponse compareVersions(Long documentId, int fromVersionNumber, int toVersionNumber, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        assertCanViewDocumentVersions(document, userId);

        DocumentVersion fromVersion = documentVersionRepository
                .findByDocumentIdAndVersionNumberAndDeleteFlgFalse(documentId, fromVersionNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND,
                        "Không tìm thấy phiên bản gốc: " + fromVersionNumber));

        DocumentVersion toVersion = documentVersionRepository
                .findByDocumentIdAndVersionNumberAndDeleteFlgFalse(documentId, toVersionNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND,
                        "Không tìm thấy phiên bản đối chiếu: " + toVersionNumber));

        boolean titleChanged = !Objects.equals(fromVersion.getTitle(), toVersion.getTitle());
        boolean summaryChanged = !Objects.equals(fromVersion.getSummary(), toVersion.getSummary());
        boolean contentChanged = !Objects.equals(fromVersion.getContent(), toVersion.getContent());

        return VersionCompareResponse.builder()
                .fromVersionNumber(fromVersionNumber)
                .toVersionNumber(toVersionNumber)
                .titleChanged(titleChanged)
                .oldTitle(fromVersion.getTitle())
                .newTitle(toVersion.getTitle())
                .summaryChanged(summaryChanged)
                .oldSummary(fromVersion.getSummary())
                .newSummary(toVersion.getSummary())
                .contentChanged(contentChanged)
                .oldContent(fromVersion.getContent())
                .newContent(toVersion.getContent())
                .build();
    }

    /**
     * Khôi phục văn bản về một phiên bản cũ được chỉ định
     */
    @Transactional
    public DocumentResponse restoreVersion(Long documentId, int versionNumber, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (readPolicy.applies(document)) readPolicy.assertDraftWrite(document, userId);
        UserLogin user = assertCanViewDocumentVersions(document, userId);

        if (document.getStatus() != DocumentStatus.DRAFT 
                && document.getStatus() != DocumentStatus.REJECTED 
                && document.getStatus() != DocumentStatus.NEEDS_REVISION) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Chỉ có thể khôi phục phiên bản khi văn bản ở trạng thái Dự thảo hoặc Cần chỉnh sửa hoặc Bị từ chối");
        }

        DocumentVersion targetVersion = documentVersionRepository
                .findByDocumentIdAndVersionNumberAndDeleteFlgFalse(documentId, versionNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND,
                        "Không tìm thấy phiên bản " + versionNumber + " để khôi phục"));

        // Cập nhật nội dung văn bản theo phiên bản cũ
        document.setTitle(targetVersion.getTitle());
        document.setSummary(targetVersion.getSummary());
        document.setContent(targetVersion.getContent());

        // Sinh phiên bản snapshot mới ghi nhận sự kiện khôi phục
        int newVersionNumber = document.getCurrentVersion() + 1;
        document.setCurrentVersion(newVersionNumber);

        DocumentVersion newSnapshot = DocumentVersion.builder()
                .document(document)
                .versionNumber(newVersionNumber)
                .title(document.getTitle())
                .summary(document.getSummary())
                .content(document.getContent())
                .changeSummary("Khôi phục dữ liệu từ phiên bản " + versionNumber)
                .createdByUser(user)
                .build();
        documentVersionRepository.save(newSnapshot);

        Document savedDoc = documentRepository.save(document);

        // Ghi nhật ký tiến trình
        DocumentProcessLog logEntry = DocumentProcessLog.builder()
                .document(savedDoc)
                .actorUser(user)
                .action(DocumentProcessAction.RESTORE_VERSION)
                .fromStatus(document.getStatus().name())
                .toStatus(document.getStatus().name())
                .note("Khôi phục nội dung từ phiên bản " + versionNumber + " sang phiên bản mới " + newVersionNumber)
                .build();
        documentProcessLogRepository.save(logEntry);

        log.info("Đã khôi phục thành công: docId={}, fromVer={}, newVer={}",
                documentId, versionNumber, newVersionNumber);

        return documentDraftService.mapToDocumentResponse(savedDoc);
    }

    private DocumentVersionResponse mapToVersionResponse(DocumentVersion ver) {
        return DocumentVersionResponse.builder()
                .id(ver.getId())
                .documentId(ver.getDocument().getId())
                .versionNumber(ver.getVersionNumber())
                .title(ver.getTitle())
                .summary(ver.getSummary())
                .content(ver.getContent())
                .changeSummary(ver.getChangeSummary())
                .createdByUserId(ver.getCreatedByUser().getId())
                .createdByUsername(ver.getCreatedByUser().getUsername())
                .createdDate(ver.getCreatedDate())
                .build();
    }
}

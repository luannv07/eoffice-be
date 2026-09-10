package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.DocumentCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentAttachmentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentVersionResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.DocumentVersion;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentVersionRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDraftService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final DocumentProcessLogRepository documentProcessLogRepository;
    private final OrgNodeRepository orgNodeRepository;
    private final UserLoginRepository userLoginRepository;
    private final UserInfoRepository userInfoRepository;
    private final DictionaryItemRepository dictionaryItemRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * UC08: Tạo dự thảo văn bản mới
     */
    @Transactional
    public DocumentResponse createDraft(DocumentCreateRequest request, Long userId) {
        UserLogin creator = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Người dùng không tồn tại"));

        OrgNode issuingOrg = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getIssuingOrgNodeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Đơn vị ban hành không tồn tại"));

        String draftCode = generateUniqueDraftCode();

        Document document = Document.builder()
                .draftCode(draftCode)
                .title(request.getTitle())
                .summary(request.getSummary())
                .content(request.getContent())
                .documentTypeCd(request.getDocumentTypeCd())
                .issuingOrgNode(issuingOrg)
                .creatorUser(creator)
                .status(DocumentStatus.DRAFT)
                .currentVersion(1)
                .securityLevel(request.getSecurityLevel())
                .urgencyLevel(request.getUrgencyLevel())
                .build();

        Document savedDocument = documentRepository.save(document);

        // Lưu phiên bản khởi tạo (Version 1)
        DocumentVersion initialVersion = DocumentVersion.builder()
                .document(savedDocument)
                .versionNumber(1)
                .title(savedDocument.getTitle())
                .summary(savedDocument.getSummary())
                .content(savedDocument.getContent())
                .changeSummary("Khởi tạo dự thảo")
                .createdByUser(creator)
                .build();
        documentVersionRepository.save(initialVersion);

        // Ghi nhật ký tiến trình
        DocumentProcessLog processLog = DocumentProcessLog.builder()
                .document(savedDocument)
                .actorUser(creator)
                .action(DocumentProcessAction.CREATE_DRAFT)
                .fromStatus(null)
                .toStatus(DocumentStatus.DRAFT.name())
                .note("Tạo mới dự thảo: " + savedDocument.getDraftCode())
                .build();
        documentProcessLogRepository.save(processLog);

        log.info("Người dùng {} đã tạo dự thảo thành công: id={}, draftCode={}",
                creator.getUsername(), savedDocument.getId(), savedDocument.getDraftCode());

        return mapToDocumentResponse(savedDocument);
    }

    /**
     * UC10: Chỉnh sửa dự thảo và lưu phiên bản mới
     */
    @Transactional
    public DocumentResponse updateDraft(Long documentId, DocumentUpdateRequest request, Long userId) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền: người tạo hoặc quản trị viên
        boolean isCreator = document.getCreatorUser().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!isCreator && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền chỉnh sửa dự thảo này");
        }

        // Kiểm tra trạng thái: chỉ cho phép sửa khi là DRAFT hoặc REJECTED
        if (document.getStatus() != DocumentStatus.DRAFT && document.getStatus() != DocumentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Không thể chỉnh sửa văn bản đang ở trạng thái: " + document.getStatus().getDescription());
        }

        OrgNode issuingOrg = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getIssuingOrgNodeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Đơn vị ban hành không tồn tại"));

        // Cập nhật thông tin
        document.setTitle(request.getTitle());
        document.setSummary(request.getSummary());
        document.setContent(request.getContent());
        document.setDocumentTypeCd(request.getDocumentTypeCd());
        document.setIssuingOrgNode(issuingOrg);
        document.setSecurityLevel(request.getSecurityLevel());
        document.setUrgencyLevel(request.getUrgencyLevel());

        // Nếu người dùng chọn lưu phiên bản mới
        if (request.isSaveNewVersion()) {
            int newVersionNumber = document.getCurrentVersion() + 1;
            document.setCurrentVersion(newVersionNumber);

            DocumentVersion version = DocumentVersion.builder()
                    .document(document)
                    .versionNumber(newVersionNumber)
                    .title(document.getTitle())
                    .summary(document.getSummary())
                    .content(document.getContent())
                    .changeSummary(request.getChangeSummary() != null && !request.getChangeSummary().isBlank()
                            ? request.getChangeSummary() : "Cập nhật phiên bản " + newVersionNumber)
                    .createdByUser(user)
                    .build();
            documentVersionRepository.save(version);
        }

        Document updatedDocument = documentRepository.save(document);

        // Ghi nhật ký tiến trình
        DocumentProcessLog processLog = DocumentProcessLog.builder()
                .document(updatedDocument)
                .actorUser(user)
                .action(DocumentProcessAction.UPDATE_DRAFT)
                .fromStatus(document.getStatus().name())
                .toStatus(document.getStatus().name())
                .note(request.getChangeSummary() != null ? request.getChangeSummary() : "Cập nhật thông tin dự thảo")
                .build();
        documentProcessLogRepository.save(processLog);

        return mapToDocumentResponse(updatedDocument);
    }

    /**
     * Lấy chi tiết dự thảo kèm tệp đính kèm và lịch sử phiên bản
     */
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDraftDetail(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        List<DocumentAttachment> attachments = documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(documentId);
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(documentId);

        List<DocumentAttachmentResponse> attachmentResponses = attachments.stream()
                .map(this::mapToAttachmentResponse)
                .toList();

        List<DocumentVersionResponse> versionResponses = versions.stream()
                .map(this::mapToVersionResponse)
                .toList();

        return DocumentDetailResponse.builder()
                .document(mapToDocumentResponse(document))
                .attachments(attachmentResponses)
                .versions(versionResponses)
                .build();
    }

    /**
     * Tìm kiếm và phân trang danh sách dự thảo
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> searchDrafts(DocumentFilterRequest filter, Long userId) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<Document> page = documentRepository.searchDocuments(
                userId, filter.getStatus(), filter.getOrgNodeId(), filter.getKeyword(), pageable);
        return page.map(this::mapToDocumentResponse);
    }

    private String generateUniqueDraftCode() {
        int year = LocalDate.now().getYear();
        String code;
        do {
            int randomNum = 10000 + RANDOM.nextInt(90000);
            code = String.format("DT-%d-%05d", year, randomNum);
        } while (documentRepository.existsByDraftCodeAndDeleteFlgFalse(code));
        return code;
    }

    public DocumentResponse mapToDocumentResponse(Document doc) {
        String typeName = resolveDocumentTypeName(doc.getDocumentTypeCd());
        String creatorFullName = resolveFullName(doc.getCreatorUser().getId(), doc.getCreatorUser().getUsername());

        return DocumentResponse.builder()
                .id(doc.getId())
                .draftCode(doc.getDraftCode())
                .title(doc.getTitle())
                .summary(doc.getSummary())
                .content(doc.getContent())
                .documentTypeCd(doc.getDocumentTypeCd())
                .documentTypeName(typeName)
                .issuingOrgNodeId(doc.getIssuingOrgNode() != null ? doc.getIssuingOrgNode().getId() : null)
                .issuingOrgNodeName(doc.getIssuingOrgNode() != null ? doc.getIssuingOrgNode().getNameVn() : null)
                .creatorUserId(doc.getCreatorUser().getId())
                .creatorUsername(doc.getCreatorUser().getUsername())
                .creatorFullName(creatorFullName)
                .status(doc.getStatus())
                .statusDescription(doc.getStatus().getDescription())
                .currentVersion(doc.getCurrentVersion())
                .securityLevel(doc.getSecurityLevel())
                .urgencyLevel(doc.getUrgencyLevel())
                .submittedAt(doc.getSubmittedAt())
                .createdDate(doc.getCreatedDate())
                .updatedDate(doc.getUpdatedDate())
                .build();
    }

    private DocumentAttachmentResponse mapToAttachmentResponse(DocumentAttachment att) {
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

    private String resolveDocumentTypeName(String code) {
        if (code == null) return null;
        Optional<DictionaryItem> item = dictionaryItemRepository
                .findByGroupCdAndItemCdAndDeleteFlgFalse("DOCUMENT_TYPE_CD", code);
        return item.map(DictionaryItem::getNameVn).orElse(code);
    }

    private String resolveFullName(Long userId, String fallbackUsername) {
        return userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(userId)
                .map(UserInfo::getFullName)
                .orElse(fallbackUsername);
    }
}

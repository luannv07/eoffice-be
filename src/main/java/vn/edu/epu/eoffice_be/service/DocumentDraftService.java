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
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
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
import vn.edu.epu.eoffice_be.security.ApprovedDocumentReadPolicy;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final ApprovedDocumentReadPolicy approvedDocumentReadPolicy;

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
                .issuingOrg(issuingOrg)
                .creatorUser(creator)
                .drafter(creator)
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
        if (document.getStatus() != DocumentStatus.DRAFT && document.getStatus() != DocumentStatus.REJECTED&& document.getStatus() != DocumentStatus.NEEDS_REVISION) {
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
        document.setIssuingOrg(issuingOrg);
        if (request.getSecurityLevel() != null) {
            document.setSecurityLevel(request.getSecurityLevel());
        }
        if (request.getUrgencyLevel() != null) {
            document.setUrgencyLevel(request.getUrgencyLevel());
        }

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
     * Xóa mềm dự thảo văn bản (chỉ khi đang ở trạng thái DRAFT)
     */
    @Transactional
    public void deleteDraft(Long documentId, Long userId) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        UserLogin creator = document.getCreatorUser() != null ? document.getCreatorUser() : document.getDrafter();
        boolean isCreator = creator != null && creator.getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!isCreator && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa dự thảo này");
        }

        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Chỉ có thể xóa văn bản đang ở trạng thái dự thảo (DRAFT)");
        }

        document.setDeleteFlg(true);
        documentRepository.save(document);

        List<DocumentAttachment> attachments = documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(documentId);
        if (!attachments.isEmpty()) {
            attachments.forEach(att -> att.setDeleteFlg(true));
            documentAttachmentRepository.saveAll(attachments);
        }

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(documentId);
        if (!versions.isEmpty()) {
            versions.forEach(ver -> ver.setDeleteFlg(true));
            documentVersionRepository.saveAll(versions);
        }

        DocumentProcessLog processLog = DocumentProcessLog.builder()
                .document(document)
                .actorUser(user)
                .action(DocumentProcessAction.DELETE_DRAFT)
                .fromStatus(document.getStatus().name())
                .toStatus(document.getStatus().name())
                .note("Xóa dự thảo: " + document.getDraftCode())
                .build();
        documentProcessLogRepository.save(processLog);

        log.info("Người dùng {} đã xóa dự thảo thành công: id={}, draftCode={}",
                user.getUsername(), document.getId(), document.getDraftCode());
    }

    /**
     * Lấy chi tiết dự thảo kèm tệp đính kèm và lịch sử phiên bản
     */
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDraftDetail(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (document.getStatus() == DocumentStatus.APPROVED) {
            if (userId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            UserLogin actor = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            approvedDocumentReadPolicy.assertCanRead(document, actor);
        }

        List<DocumentAttachment> attachments = documentAttachmentRepository.findByDocumentIdAndDeleteFlgFalse(documentId);
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdAndDeleteFlgFalseOrderByVersionNumberDesc(documentId);

        List<DocumentAttachmentResponse> attachmentResponses = attachments.stream()
                .map(this::mapToAttachmentResponse)
                .toList();

        List<DocumentVersionResponse> versionResponses = versions.stream()
                .map(this::mapToVersionResponse)
                .toList();

        UserLogin creator = document.getCreatorUser() != null ? document.getCreatorUser() : document.getDrafter();
        OrgNode org = document.getIssuingOrgNode() != null ? document.getIssuingOrgNode() : document.getIssuingOrg();
        DocumentStatus status = document.getStatus() != null ? document.getStatus() : DocumentStatus.DRAFT;

        return DocumentDetailResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .documentTypeCd(document.getDocumentTypeCd())
                .issuingOrgId(org != null ? org.getId() : null)
                .issuingOrgName(org != null ? org.getNameVn() : null)
                .drafterId(creator != null ? creator.getId() : null)
                .drafterUsername(creator != null ? creator.getUsername() : null)
                .signerId(document.getSigner() != null ? document.getSigner().getId() : null)
                .signerUsername(document.getSigner() != null ? document.getSigner().getUsername() : null)
                .urgencyLevelCd(document.getUrgencyLevelCd())
                .securityLevelCd(document.getSecurityLevelCd())
                .status(status)
                .officialNumber(document.getOfficialNumber())
                .issueDate(document.getIssueDate())
                .effectiveDate(document.getEffectiveDate())
                .officialHash(document.getOfficialHash())
                .primaryFileUrl(document.getPrimaryFilePath() != null ? "/documents/" + document.getId() + "/files/primary" : null)
                .officialFileUrl(document.getOfficialFilePath() != null ? "/documents/" + document.getId() + "/files/official" : null)
                .recipientsText(document.getRecipientsText())
                .document(mapToDocumentResponse(document))
                .attachments(attachmentResponses)
                .versions(versionResponses)
                .build();
    }

    /**
     * Tìm kiếm và phân trang danh sách dự thảo văn bản.
     * Mặc định sắp xếp giảm dần theo thời gian tạo (createdDate DESC) từ BaseEntity.
     *
     * @param filter Bộ lọc tìm kiếm và thông tin phân trang
     * @param userId ID người dùng yêu cầu (dùng để lọc văn bản của chính họ nếu không có quyền xem tất cả)
     * @return Trang kết quả danh sách dự thảo văn bản đã được sắp xếp
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> searchDrafts(DocumentFilterRequest filter, Long userId) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(Sort.Direction.DESC, "createdDate"));
        String keyword = filter.getKeyword();
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                // Chuẩn hóa chuỗi rỗng hoặc chỉ chứa khoảng trắng về null.
                // DocumentRepository.searchDocuments hỗ trợ cả (:keyword IS NULL OR :keyword = '')
                keyword = null;
            } else {
                // Thoát các ký tự đại diện SQL LIKE ('\', '%', '_') để khớp chính xác với mệnh đề ESCAPE '\' trong DocumentRepository
                keyword = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            }
        }
        Long creatorUserId = userId;
        if (userId != null) {
            UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId).orElse(null);
            if (user != null && (user.getRole() == Role.CLERK || user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN)) {
                creatorUserId = null;
            }
        }
        Page<Document> page = documentRepository.searchDocuments(
                creatorUserId, filter.getStatus(), filter.getOrgNodeId(), keyword, pageable);
        return page.map(this::mapToDocumentResponse);
    }

    private String generateUniqueDraftCode() {
        int year = LocalDate.now(ZoneId.systemDefault()).getYear();
        String code;
        do {
            int randomNum = 10000 + RANDOM.nextInt(90000);
            code = String.format("DT-%d-%05d", year, randomNum);
        } while (documentRepository.existsByDraftCodeAndDeleteFlgFalse(code));
        return code;
    }

    public DocumentResponse mapToDocumentResponse(Document doc) {
        String typeName = resolveDocumentTypeName(doc.getDocumentTypeCd());
        UserLogin creator = doc.getCreatorUser() != null ? doc.getCreatorUser() : doc.getDrafter();
        Long creatorId = creator != null ? creator.getId() : null;
        String creatorUsername = creator != null ? creator.getUsername() : null;
        String creatorFullName = (creatorId != null && creatorUsername != null)
                ? resolveFullName(creatorId, creatorUsername)
                : creatorUsername;

        OrgNode org = doc.getIssuingOrgNode() != null ? doc.getIssuingOrgNode() : doc.getIssuingOrg();
        Long orgId = org != null ? org.getId() : null;
        String orgName = org != null ? org.getNameVn() : null;

        DocumentStatus status = doc.getStatus() != null ? doc.getStatus() : DocumentStatus.DRAFT;

        return DocumentResponse.builder()
                .id(doc.getId())
                .draftCode(doc.getDraftCode())
                .title(doc.getTitle())
                .summary(doc.getSummary())
                .content(doc.getContent())
                .documentTypeCd(doc.getDocumentTypeCd())
                .documentTypeName(typeName)
                .issuingOrgNodeId(orgId)
                .issuingOrgNodeName(orgName)
                .creatorUserId(creatorId)
                .creatorUsername(creatorUsername)
                .creatorFullName(creatorFullName)
                .status(status)
                .statusDescription(status.getDescription())
                .currentVersion(doc.getCurrentVersion())
                .securityLevel(doc.getSecurityLevel() != null ? doc.getSecurityLevel() : DocumentSecurityLevel.NORMAL)
                .urgencyLevel(doc.getUrgencyLevel() != null ? doc.getUrgencyLevel() : DocumentUrgencyLevel.NORMAL)
                .submittedAt(doc.getSubmittedAt())
                .createdDate(doc.getCreatedDate())
                .updatedDate(doc.getUpdatedDate())
                .build();
    }

    private DocumentAttachmentResponse mapToAttachmentResponse(DocumentAttachment att) {
        UserLogin uploader = att.getUploadedByUser();
        return DocumentAttachmentResponse.builder()
                .id(att.getId())
                .documentId(att.getDocument() != null ? att.getDocument().getId() : null)
                .documentVersionId(att.getDocumentVersion() != null ? att.getDocumentVersion().getId() : null)
                .originalFileName(att.getOriginalFileName())
                .fileExtension(att.getFileExtension())
                .mimeType(att.getMimeType())
                .fileSizeBytes(att.getFileSizeBytes())
                .sha256Checksum(att.getSha256Checksum())
                .isMainDocument(att.isMainDocument())
                .uploadedByUserId(uploader != null ? uploader.getId() : null)
                .uploadedByUsername(uploader != null ? uploader.getUsername() : null)
                .createdDate(att.getCreatedDate())
                .build();
    }

    private DocumentVersionResponse mapToVersionResponse(DocumentVersion ver) {
        UserLogin creator = ver.getCreatedByUser();
        return DocumentVersionResponse.builder()
                .id(ver.getId())
                .documentId(ver.getDocument() != null ? ver.getDocument().getId() : null)
                .versionNumber(ver.getVersionNumber())
                .title(ver.getTitle())
                .summary(ver.getSummary())
                .content(ver.getContent())
                .changeSummary(ver.getChangeSummary())
                .createdByUserId(creator != null ? creator.getId() : null)
                .createdByUsername(creator != null ? creator.getUsername() : null)
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

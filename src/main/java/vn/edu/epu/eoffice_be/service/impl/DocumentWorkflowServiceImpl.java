package vn.edu.epu.eoffice_be.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;
import vn.edu.epu.eoffice_be.service.FileStorageService;
import vn.edu.epu.eoffice_be.service.PdfProcessingService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class DocumentWorkflowServiceImpl implements DocumentWorkflowService {

    private final DocumentRepository documentRepository;
    private final DocumentWorkflowLogRepository documentWorkflowLogRepository;
    private final UserLoginRepository userLoginRepository;
    private final UserInfoRepository userInfoRepository;
    private final OrgNodeRepository orgNodeRepository;
    private final WorkflowEngine workflowEngine;
    private final FileStorageService fileStorageService;
    private final PdfProcessingService pdfProcessingService;

    public DocumentWorkflowServiceImpl(DocumentRepository documentRepository,
                                       DocumentWorkflowLogRepository documentWorkflowLogRepository) {
        this(documentRepository, documentWorkflowLogRepository, null, null, null, null, null, null);
    }

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    @Transactional
    public DocumentDetailResponse approve(Long documentId, DocumentApproveRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        OrgNode assignedOrg = null;
        if (request.getAssignedOrgNodeId() != null) {
            assignedOrg = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getAssignedOrgNodeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_NOT_FOUND));
        }

        // Quyết định hành động chuyển tiếp phù hợp với trạng thái hiện tại
        WorkflowAction action;
        if (document.getStatus() == DocumentStatus.SUBMITTED || document.getStatus() == DocumentStatus.IN_REVIEW) {
            action = WorkflowAction.REVIEW_APPROVE;
        } else if (document.getStatus() == DocumentStatus.LEADER_REVIEW) {
            action = WorkflowAction.LEADER_APPROVE;
        } else {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE);
        }

        // Kiểm tra thẩm quyền người xử lý trước khi thực hiện chuyển đổi
        validateActorAuthorization(document, actor, action);

        workflowEngine.executeTransition(
                document,
                action,
                actor,
                request.getComment(),
                request.getLeadershipInstruction(),
                assignedOrg,
                null
        );

        return mapToDetailResponse(document);
    }

    @Override
    @Transactional
    public DocumentDetailResponse requestRevision(Long documentId, DocumentRevisionRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // Kiểm tra thẩm quyền người yêu cầu chỉnh sửa
        validateActorAuthorization(document, actor, WorkflowAction.REQUEST_REVISION);

        workflowEngine.executeTransition(
                document,
                WorkflowAction.REQUEST_REVISION,
                actor,
                request.getComment(),
                null,
                null,
                null
        );

        return mapToDetailResponse(document);
    }

    @Override
    public DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // Kiểm tra thẩm quyền ký nháy TRƯỚC KHI xử lý file PDF nhằm tránh lãng phí tài nguyên CPU/IO
        validateActorAuthorization(document, actor, WorkflowAction.INITIAL_SIGN);

        // 1. Kiểm tra file PDF nguồn: nếu chưa có file đính kèm chính thì báo lỗi
        ensureDocumentPdfExists(document);

        byte[] currentPdfBytes = fileStorageService.loadFileBytes(document.getPrimaryFilePath());

        // 2. Lấy thông tin người ký để in con dấu
        String signerFullName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(actor.getId())
                .map(UserInfo::getFullName)
                .orElse(actor.getUsername());

        StampInfo stampInfo = StampInfo.builder()
                .signerName(signerFullName)
                .positionTitle("Can bo tham dinh")
                .signDate(DATE_TIME_FORMATTER.format(Instant.now()))
                .note("Tham dinh noi dung")
                .build();

        // 3. Gọi PDFBox vẽ con dấu ký nháy vào trang cuối file PDF
        byte[] stampedPdfBytes = pdfProcessingService.applyInitialSignStamp(currentPdfBytes, stampInfo);

        // 4. Lưu bản PDF mới vào storage và tính mã băm SHA-256
        String newPdfPath = fileStorageService.storeFile(
                stampedPdfBytes,
                "initial_signed_" + document.getId() + ".pdf",
                "drafts"
        );
        document.setPrimaryFilePath(newPdfPath);

        String signatureHash = fileStorageService.computeSha256(stampedPdfBytes);

        // 5. Điều hướng luồng duyệt và ghi log (chạy trong @Transactional của WorkflowEngine)
        try {
            workflowEngine.executeTransition(
                    document,
                    WorkflowAction.INITIAL_SIGN,
                    actor,
                    request.getComment(),
                    null,
                    null,
                    signatureHash
            );
        } catch (Exception ex) {
            fileStorageService.deleteFile(newPdfPath);
            throw ex;
        }

        return mapToDetailResponse(document);
    }

    @Override
    public DocumentDetailResponse signAndPublish(Long documentId, DocumentPublishRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // 1. Cập nhật thông tin ban hành chính thức
        Instant issueDate = request.getIssueDate() != null ? request.getIssueDate() : Instant.now();
        Instant effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : issueDate;

        document.setOfficialNumber(request.getOfficialNumber());
        document.setIssueDate(issueDate);
        document.setEffectiveDate(effectiveDate);

        // Nếu văn bản chưa có người ký được gán trước đó, gán actor làm signer tại thời điểm ký phát hành
        if (document.getSigner() == null) {
            document.setSigner(actor);
        }

        // Kiểm tra thẩm quyền ký phát hành TRƯỚC KHI xử lý ký số PKI nhằm tránh lãng phí tài nguyên
        validateActorAuthorization(document, actor, WorkflowAction.SIGN_AND_PUBLISH);

        // 2. Kiểm tra file PDF nguồn: nếu chưa có file đính kèm chính thì báo lỗi
        ensureDocumentPdfExists(document);
        byte[] currentPdfBytes = fileStorageService.loadFileBytes(document.getPrimaryFilePath());

        // 3. Ký số điện tử PKI X.509 PAdES và đặt cờ niêm phong CERTIFIED_NO_CHANGES_ALLOWED
        String signerDisplayName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(actor.getId())
                .map(UserInfo::getFullName)
                .orElse(actor.getUsername());

        byte[] certifiedPdfBytes = pdfProcessingService.signAndCertifyPdf(
                currentPdfBytes,
                signerDisplayName,
                "Ban hanh van ban: " + request.getOfficialNumber()
        );

        // 4. Lưu file PDF chính thức vào thư mục riêng biệt "official"
        String officialPath = fileStorageService.storeFile(
                certifiedPdfBytes,
                "official_" + document.getId() + ".pdf",
                "official"
        );
        String officialHash = fileStorageService.computeSha256(certifiedPdfBytes);

        document.setOfficialFilePath(officialPath);
        document.setOfficialHash(officialHash);

        // 5. Chuyển trạng thái sang PUBLISHED và ghi log (chạy trong @Transactional của WorkflowEngine)
        try {
            workflowEngine.executeTransition(
                    document,
                    WorkflowAction.SIGN_AND_PUBLISH,
                    actor,
                    request.getComment(),
                    null,
                    null,
                    officialHash
            );
        } catch (Exception ex) {
            fileStorageService.deleteFile(officialPath);
            throw ex;
        }

        return mapToDetailResponse(document);
    }

    @Override
    @Transactional
    public DocumentDetailResponse reject(Long documentId, DocumentRejectRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // Kiểm tra thẩm quyền từ chối văn bản
        validateActorAuthorization(document, actor, WorkflowAction.REJECT);

        workflowEngine.executeTransition(
                document,
                WorkflowAction.REJECT,
                actor,
                request.getComment(),
                null,
                null,
                null
        );

        return mapToDetailResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentWorkflowLogResponse> getWorkflowHistory(Long documentId, Pageable pageable) {
        findDocumentOrThrow(documentId);
        Pageable effectivePageable = (pageable == null || pageable.isUnpaged())
                ? PageRequest.of(0, 20, Sort.by("createdDate").ascending())
                : pageable;
        if (effectivePageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(
                    effectivePageable.getPageNumber(),
                    effectivePageable.getPageSize(),
                    Sort.by("createdDate").ascending()
            );
        }
        Page<DocumentWorkflowLog> logs =
                documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalse(documentId, effectivePageable);

        return logs.map(this::mapToLogResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDetail(Long documentId) {
        return mapToDetailResponse(findDocumentOrThrow(documentId));
    }

    private Document findDocumentOrThrow(Long id) {
        return documentRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private UserLogin findActorOrThrow(Long id) {
        return userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private Long getCurrentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtUserPrincipal jwtPrincipal) {
            return jwtPrincipal.getUserId();
        }
        if (principal instanceof String principalStr) {
            try {
                return Long.valueOf(principalStr);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private UserLogin getCurrentActor() {
        return findActorOrThrow(getCurrentActorId());
    }

    private void ensureDocumentPdfExists(Document document) {
        if (document.getPrimaryFilePath() == null || document.getPrimaryFilePath().isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING);
        }
    }

    /**
     * Xác thực thẩm quyền của người dùng hiện tại đối với hành động trên văn bản,
     * ủy quyền cho WorkflowEngine kiểm tra chi tiết theo vai trò và người được gán xử lý.
     *
     * @param document Văn bản đang được xử lý
     * @param actor    Người dùng hiện tại thực hiện hành động
     * @param action   Hành động luồng duyệt yêu cầu
     * @throws BusinessException nếu người dùng không đủ quyền thực hiện hành động
     */
    private void validateActorAuthorization(Document document, UserLogin actor, WorkflowAction action) {
        workflowEngine.validateActorPermission(document, actor, action);
    }

    private DocumentDetailResponse mapToDetailResponse(Document doc) {
        return DocumentDetailResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .documentTypeCd(doc.getDocumentTypeCd())
                .issuingOrgId(doc.getIssuingOrg() != null ? doc.getIssuingOrg().getId() : null)
                .issuingOrgName(doc.getIssuingOrg() != null ? doc.getIssuingOrg().getNameVn() : null)
                .drafterId(doc.getDrafter() != null ? doc.getDrafter().getId() : null)
                .drafterUsername(doc.getDrafter() != null ? doc.getDrafter().getUsername() : null)
                .signerId(doc.getSigner() != null ? doc.getSigner().getId() : null)
                .signerUsername(doc.getSigner() != null ? doc.getSigner().getUsername() : null)
                .urgencyLevelCd(doc.getUrgencyLevelCd())
                .securityLevelCd(doc.getSecurityLevelCd())
                .status(doc.getStatus())
                .officialNumber(doc.getOfficialNumber())
                .issueDate(doc.getIssueDate())
                .effectiveDate(doc.getEffectiveDate())
                .officialHash(doc.getOfficialHash())
                .primaryFileUrl(resolveFileUrl(doc.getId(), "primary", doc.getPrimaryFilePath()))
                .officialFileUrl(resolveFileUrl(doc.getId(), "official", doc.getOfficialFilePath()))
                .recipientsText(doc.getRecipientsText())
                .build();
    }

    private String resolveFileUrl(Long documentId, String fileType, String filePath) {
        if (documentId == null || filePath == null || filePath.isBlank()) {
            return null;
        }
        return "/documents/" + documentId + "/files/" + fileType;
    }

    private DocumentWorkflowLogResponse mapToLogResponse(DocumentWorkflowLog log) {
        return DocumentWorkflowLogResponse.builder()
                .id(log.getId())
                .documentId(log.getDocument().getId())
                .action(log.getAction())
                .actionName(log.getAction().getDescription())
                .actorId(log.getActor().getId())
                .actorUsername(log.getActor().getUsername())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .comment(log.getComment())
                .leadershipInstruction(log.getLeadershipInstruction())
                .assignedOrgNodeId(log.getAssignedOrgNode() != null ? log.getAssignedOrgNode().getId() : null)
                .assignedOrgNodeName(log.getAssignedOrgNode() != null ? log.getAssignedOrgNode().getNameVn() : null)
                .signatureHash(log.getSignatureHash())
                .createdDate(log.getCreatedDate())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId) {
        List<DocumentWorkflowLog> logs = documentWorkflowLogRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        if (logs == null || logs.isEmpty()) {
            logs = documentWorkflowLogRepository.findByDocumentIdOrderByCreatedDateAsc(documentId);
        }
        return logs.stream()
                .map(log -> WorkflowTimelineResponse.builder()
                        .id(log.getId())
                        .documentId(log.getDocument() != null ? log.getDocument().getId() : null)
                        .actorUsername(log.getActor() != null ? log.getActor().getUsername() : null)
                        .actorRole(null)
                        .action(log.getAction() != null ? log.getAction().name() : null)
                        .comment(log.getComment())
                        .createdAt(log.getCreatedDate() != null ? LocalDateTime.ofInstant(log.getCreatedDate(), ZONE_VN) : null)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PendingDocumentResponse> getPendingDocuments(Pageable pageable) {
        Page<Document> page = documentRepository.findByStatusIn(
                List.of("PENDING_APPROVAL", "IN_REVIEW", "WAITING_SIGN", "SUBMITTED", "DRAFT"),
                pageable);
        if (page == null) {
            return Page.empty(pageable);
        }
        return page.map(doc -> PendingDocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .currentHolder("Lãnh đạo phê duyệt")
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingDocumentResponse> getPendingDocuments() {
        List<Document> list = documentRepository.findByStatusIn(
                List.of("PENDING_APPROVAL", "IN_REVIEW", "WAITING_SIGN", "SUBMITTED", "DRAFT"));
        if (list == null || list.isEmpty()) {
            list = documentRepository.findByStatusAndDeleteFlgFalse(DocumentStatus.PENDING_APPROVAL);
        }
        if (list == null) {
            list = List.of();
        }
        return list.stream().map(doc -> PendingDocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .currentHolder("Lãnh đạo phê duyệt")
                .build()).toList();
    }

    @Override
    public void logAction(Long documentId, String username, String role, String action, String comment) {
        WorkflowAction act = null;
        if (action != null && !action.isBlank()) {
            try {
                act = WorkflowAction.valueOf(action.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Keep act as null for default fallback
            }
        }
        if (act == null) {
            act = WorkflowAction.SUBMIT;
        }
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .document(Document.builder().id(documentId).build())
                .action(act)
                .comment(comment)
                .build();
        documentWorkflowLogRepository.save(log);
    }
}

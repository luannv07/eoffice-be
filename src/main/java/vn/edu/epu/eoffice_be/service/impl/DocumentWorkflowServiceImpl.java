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

import vn.edu.epu.eoffice_be.common.enums.DistributionScope;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentLeaderSignRequest;
import vn.edu.epu.eoffice_be.common.enums.InitialSignStage;
import vn.edu.epu.eoffice_be.service.DocumentSignatureGuard;
import vn.edu.epu.eoffice_be.service.DocumentWorkingPdfService;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevokeRequest;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.SignatureItemResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.pki.DuplicateSignatureException;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.security.pki.PdfSignatureVerifier;
import vn.edu.epu.eoffice_be.security.pki.SignaturePosition;
import vn.edu.epu.eoffice_be.service.DocumentWorkflowService;
import vn.edu.epu.eoffice_be.service.FileStorageService;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;
import vn.edu.epu.eoffice_be.repository.DocumentDistributionRepository;

import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class DocumentWorkflowServiceImpl implements DocumentWorkflowService {

    private static final int MAX_RECIPIENTS = 200;

    private final DocumentRepository documentRepository;
    private final DocumentWorkflowLogRepository documentWorkflowLogRepository;
    private final UserLoginRepository userLoginRepository;
    private final UserInfoRepository userInfoRepository;
    private final OrgNodeRepository orgNodeRepository;
    private final WorkflowEngine workflowEngine;
    private final FileStorageService fileStorageService;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final PdfSigner pdfSigner;
    private final PositionAssignmentRepository positionAssignmentRepository;
    private final PdfSignatureVerifier pdfSignatureVerifier;
    private final DocumentPdfStorageService documentPdfStorageService;
    private final DocumentDistributionRepository distributionRepository;
    private final DocumentSignatureGuard signatureGuard;
    private final DocumentWorkingPdfService workingPdf;

    public DocumentWorkflowServiceImpl(DocumentRepository documentRepository,
                                       DocumentWorkflowLogRepository documentWorkflowLogRepository) {
        this(
                documentRepository,
                documentWorkflowLogRepository,
                null, // userLoginRepository
                null, // userInfoRepository
                null, // orgNodeRepository
                null, // workflowEngine
                null, // fileStorageService
                null, // documentAttachmentRepository
                null, // pdfSigner
                null, // positionAssignmentRepository
                null, // pdfSignatureVerifier
                null,  // documentPdfStorageService
                null, // distributionRepository
                null, // signatureGuard
                null // workingPdf
        );
    }

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final List<DocumentStatus> PENDING_STATUSES = List.of(
        DocumentStatus.IN_REVIEW,
        DocumentStatus.PENDING_APPROVAL,
        DocumentStatus.PENDING_SIGN,
        DocumentStatus.APPROVED
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailResponse approve(Long documentId, DocumentApproveRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);
        WorkflowAction action;
        if (document.getStatus() == DocumentStatus.IN_REVIEW) action = WorkflowAction.REVIEW_APPROVE;
        else if (document.getStatus() == DocumentStatus.PENDING_APPROVAL) action = WorkflowAction.LEADER_APPROVE;
        else throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE);
        workflowEngine.validateTransition(document, action, request.getComment());
        validateActorAuthorization(document, actor, action);
        byte[] pdf = loadDocumentPdfBytes(document);
        if (action == WorkflowAction.LEADER_APPROVE) signatureGuard.requireLeader(document, actor, pdf);
        signatureGuard.requireInitial(document, actor,
                action == WorkflowAction.REVIEW_APPROVE ? InitialSignStage.REVIEWER : InitialSignStage.LEADER, pdf);
        OrgNode assignedOrg = request.getAssignedOrgNodeId() == null ? null : orgNodeRepository
                .findByIdAndDeleteFlgFalse(request.getAssignedOrgNodeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_NOT_FOUND));
        workflowEngine.executeTransition(document, action, actor, request.getComment(),
                request.getLeadershipInstruction(), assignedOrg, null);
        return mapToDetailResponse(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailResponse leaderSign(Long documentId, DocumentLeaderSignRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);
        workflowEngine.validateTransition(document, WorkflowAction.LEADER_SIGN, request.getComment());
        validateActorAuthorization(document, actor, WorkflowAction.LEADER_SIGN);
        byte[] pdf = loadDocumentPdfBytes(document);
        String fullName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(actor.getId())
                .map(UserInfo::getFullName).orElse(actor.getUsername());
        SignerTitleInfo title = resolveSignerTitleInfo(actor);
        PdfSigner.SignResult result;
        try {
            result = pdfSigner.signVisibleBytes(pdf, actor.getId().toString(), fullName,
                    title.title(), title.orgUnit(),
                    request.getComment() == null || request.getComment().isBlank()
                            ? "Hiệu trưởng ký phê duyệt văn bản" : request.getComment(),
                    resolveApproveSignaturePosition(request));
        } catch (DuplicateSignatureException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Hiệu trưởng đã ký hình ảnh trên PDF này");
        }
        String reference = documentPdfStorageService.storeSignedPdf(result.signedPdfBytes(),
                "leader_signed_" + documentId + ".pdf", documentId);
        document.setPrimaryFilePath(reference);
        if (document.getSigner() == null) document.setSigner(actor);
        workflowEngine.executeTransition(document, WorkflowAction.LEADER_SIGN, actor,
                request.getComment(), null, null, result.outPdfSha256());
        return mapToDetailResponse(document);
    }

    private SignaturePosition resolveApproveSignaturePosition(DocumentLeaderSignRequest request) {
        if (request == null) {
            return new SignaturePosition(9999, 380f, 120f, 150f, 80f);
        }
        int page = (request.getSignaturePage() != null && request.getSignaturePage() > 0)
                ? request.getSignaturePage()
                : 9999;
        float x = request.getSignatureX() != null ? request.getSignatureX() : 380f;
        float y = request.getSignatureY() != null ? request.getSignatureY() : 120f;
        float width = (request.getSignatureWidth() != null && request.getSignatureWidth() > 0)
                ? request.getSignatureWidth()
                : 150f;
        float height = (request.getSignatureHeight() != null && request.getSignatureHeight() > 0)
                ? request.getSignatureHeight()
                : 80f;
        return new SignaturePosition(page, x, y, width, height);
    }

    private SignaturePosition resolveSignaturePosition(DocumentPublishRequest request) {
        if (request == null) {
            return new SignaturePosition(1, 350f, 150f, 180f, 60f);
        }
        int page = (request.getSignaturePage() != null && request.getSignaturePage() > 0)
                ? request.getSignaturePage()
                : 1;
        float x = request.getSignatureX() != null ? request.getSignatureX() : 350f;
        float y = request.getSignatureY() != null ? request.getSignatureY() : 150f;
        float width = (request.getSignatureWidth() != null && request.getSignatureWidth() > 0)
                ? request.getSignatureWidth()
                : 180f;
        float height = (request.getSignatureHeight() != null && request.getSignatureHeight() > 0)
                ? request.getSignatureHeight()
                : 60f;
        return new SignaturePosition(page, x, y, width, height);
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
        freezePreviousDocumentFiles(document);
        documentRepository.save(document);
        return mapToDetailResponse(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        workflowEngine.validateTransition(document, WorkflowAction.INITIAL_SIGN, request.getComment());
        validateActorAuthorization(document, actor, WorkflowAction.INITIAL_SIGN);
        InitialSignStage stage = InitialSignStage.fromStatus(document.getStatus());

        // 1. Kiểm tra file PDF nguồn: nếu chưa có file đính kèm chính thì báo lỗi

        byte[] currentPdfBytes = loadDocumentPdfBytes(document);
        if (stage == InitialSignStage.LEADER) signatureGuard.requireLeader(document, actor, currentPdfBytes);

        // 2. Lấy thông tin người ký để cấp chứng thư hoặc ghi nhận danh tính
        String signerFullName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(actor.getId())
                .map(UserInfo::getFullName)
                .orElse(actor.getUsername());
        SignerTitleInfo titleInfo = resolveSignerTitleInfo(actor);
        String signerDisplayName = (titleInfo.title() != null && !titleInfo.title().isBlank())
                ? signerFullName + " - " + titleInfo.title()
                : signerFullName;

        // 3. Thực hiện Ký nháy số chuẩn BouncyCastle CMS Detached bằng PdfSigner (lưu tăng dần, bảo toàn chữ ký cũ)
        String signReason = (request.getComment() != null && !request.getComment().isBlank())
                ? request.getComment()
                : "Ký nháy văn bản";
        PdfSigner.SignResult signResult;
        try {
            signResult = pdfSigner.signInitialBytes(
                    currentPdfBytes,
                    String.valueOf(actor.getId()),
                    signerFullName,
                    titleInfo.title(),
                    titleInfo.orgUnit(),
                    signReason,
                    stage
            );
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("User đã ký")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Người dùng [ID=" + actor.getId() + "] đã thực hiện ký trên tài liệu này rồi, không được phép ký đè lần 2!");
            }
            throw ex;
        }
        byte[] stampedPdfBytes = signResult.signedPdfBytes();

        // 4. Lưu bản PDF mới vào storage và lấy mã băm SHA-256 từ chữ ký số
        String newPdfReference = documentPdfStorageService.storeSignedPdf(
                stampedPdfBytes,
                "initial_signed_" + document.getId() + ".pdf",
                document.getId()
        );
        document.setPrimaryFilePath(newPdfReference);

        String signatureHash = signResult.outPdfSha256();

        // 5. Điều hướng luồng duyệt và ghi log (chạy trong @Transactional của WorkflowEngine)
        // Storage cleans up the uploaded version if this transaction rolls back.
        workflowEngine.executeTransition(
                document,
                WorkflowAction.INITIAL_SIGN,
                actor,
                request.getComment(),
                null,
                null,
                signatureHash
        );

        return mapToDetailResponse(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailResponse signAndPublish(Long documentId, DocumentPublishRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // 1. Cập nhật thông tin ban hành chính thức
        Instant issueDate = request.getIssueDate() != null ? request.getIssueDate() : Instant.now();
        Instant effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : issueDate;

        if (effectiveDate.isBefore(issueDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày có hiệu lực (effectiveDate) không được trước ngày ban hành (issueDate)");
        }

        workflowEngine.validateTransition(document, WorkflowAction.SIGN_AND_PUBLISH, request.getComment());
        if (document.getDocumentBook() == null || document.getOfficialNumber() == null
                || document.getOfficialNumber().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Văn thư phải cấp số từ sổ văn bản trước khi ký nháy và phát hành");
        }

        // Kiểm tra thẩm quyền ký phát hành TRƯỚC KHI xử lý ký số PKI nhằm tránh lãng phí tài nguyên
        validateActorAuthorization(document, actor, WorkflowAction.SIGN_AND_PUBLISH);

        List<Long> recipientOrgIds = validateRecipients(request);

        // Kiểm tra file PDF nguồn TRƯỚC KHI cấp số và ký số: nếu chưa có file đính kèm chính thì báo lỗi

        String officialNumber = document.getOfficialNumber();

        // Kiểm tra null/blank trước khi gọi .trim() để tránh NullPointerException
        if (!org.springframework.util.StringUtils.hasText(officialNumber)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Số hiệu văn bản không được để trống");
        }
        String trimmedOfficialNumber = officialNumber.trim();

        // Kiểm tra chống trùng số văn bản đã có trên hệ thống
        if (documentRepository.existsByOfficialNumberAndIdNotAndDeleteFlgFalse(trimmedOfficialNumber, document.getId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NUMBER_ALREADY_EXISTS,
                    "Số hiệu văn bản '" + trimmedOfficialNumber + "' đã tồn tại trên một văn bản khác trong hệ thống!");
        }

        document.setOfficialNumber(trimmedOfficialNumber);
        document.setIssueDate(issueDate);
        document.setEffectiveDate(effectiveDate);

        // 3. Đọc file PDF nguồn
        byte[] currentPdfBytes = loadDocumentPdfBytes(document);
        UserLogin leader = document.getSigner();
        if (leader == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Văn bản chưa có người ký phê duyệt");
        signatureGuard.requireLeader(document, leader, currentPdfBytes);
        signatureGuard.requireInitial(document, leader, InitialSignStage.LEADER, currentPdfBytes);
        signatureGuard.requireInitial(document, actor, InitialSignStage.CLERK, currentPdfBytes);

        // 4. Ký số điện tử PKI X.509 PAdES và đặt cờ niêm phong CERTIFIED_NO_CHANGES_ALLOWED
        SignerTitleInfo titleInfo = resolveSignerTitleInfo(actor);
        String signerFullName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(actor.getId())
                .map(UserInfo::getFullName)
                .orElse(actor.getUsername());
        String signerDisplayName = (titleInfo.title() != null && !titleInfo.title().isBlank())
                ? signerFullName + " - " + titleInfo.title()
                : signerFullName;

        // Clerk initials were applied by the explicit initial-sign endpoint.
        byte[] pdfToCertify = currentPdfBytes;

        // 4.2. Ban hành chính thức kèm khóa niêm phong DocMDP (P=1: CERTIFIED_NO_CHANGES_ALLOWED)
        SignaturePosition pos = resolveSignaturePosition(request);
        PdfSigner.SignResult signResult;
        try {
            signResult = pdfSigner.signAndCertifyBytes(
                    pdfToCertify,
                    actor.getId().toString(),
                    signerDisplayName,
                    titleInfo.title(),
                    titleInfo.orgUnit(),
                    "Ban hanh van ban: " + officialNumber,
                    pos
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Lỗi ký số phát hành văn bản id={}", document.getId(), ex);
            throw new BusinessException(ErrorCode.SIGNATURE_PROCESS_ERROR,
                    "Không thể ký số phát hành văn bản");
        }
        byte[] certifiedPdfBytes = signResult.signedPdfBytes();

        // 4.3. Store the final certified PDF and retain its durable storage reference.
        String officialReference = documentPdfStorageService.storeSignedPdf(
                certifiedPdfBytes,
                "official_" + document.getId() + ".pdf",
                document.getId()
        );
        String officialHash = fileStorageService.computeSha256(certifiedPdfBytes);

        document.setOfficialFilePath(officialReference);
        document.setOfficialHash(officialHash);

        // 5. Chuyển trạng thái sang PUBLISHED và ghi log (chạy trong @Transactional của WorkflowEngine)
        // Storage discards this upload if the transaction rolls back.
        workflowEngine.executeTransition(
                document,
                WorkflowAction.SIGN_AND_PUBLISH,
                actor,
                request.getComment(),
                null,
                null,
                officialHash
        );

        saveDistributions(document.getId(), request.getDistributionScope(), recipientOrgIds);

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
        freezePreviousDocumentFiles(document);
        documentRepository.save(document);

        return mapToDetailResponse(document);
    }

    private void freezePreviousDocumentFiles(Document document) {
        document.setPrimaryFilePath(null);
        if (documentAttachmentRepository != null && document.getId() != null) {
            List<DocumentAttachment> mainAttachments = documentAttachmentRepository
                    .findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(document.getId());
            if (mainAttachments != null && !mainAttachments.isEmpty()) {
                for (DocumentAttachment att : mainAttachments) {
                    att.setMainDocument(false);
                    documentAttachmentRepository.save(att);
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentWorkflowLogResponse> getWorkflowHistory(Long documentId, Pageable pageable) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = getCurrentActor();
        validateDocumentReadAccess(document, actor);

        Pageable effectivePageable;
        if (pageable == null || pageable.isUnpaged()) {
            effectivePageable = PageRequest.of(
                    0,
                    20,
                    Sort.by(Sort.Order.asc("createdDate"), Sort.Order.asc("id"))
            );
        } else {
            Sort sort = pageable.getSort();
            if (sort.isUnsorted()) {
                sort = Sort.by(Sort.Order.asc("createdDate"), Sort.Order.asc("id"));
            } else if (sort.getOrderFor("id") == null) {
                sort = sort.and(Sort.by(Sort.Order.asc("id")));
            }
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }

        return documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalse(documentId, effectivePageable)
                .map(this::mapToLogResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDetail(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = getCurrentActor();
        validateDocumentReadAccess(document, actor);
        return mapToDetailResponse(document);
    }

    /**
     * Kiểm tra quyền đọc/xem chi tiết văn bản và lịch sử xử lý (Issue 1).
     * Cho phép: Quản trị viên (ADMIN, SYSTEM_ADMIN), người tạo (drafter),
     * người ký (signer), vai trò xét duyệt (LEADER, REVIEWER),
     * hoặc khi văn bản đã được ban hành chính thức (PUBLISHED).
     */
    private void validateDocumentReadAccess(Document document, UserLogin actor) {
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Role role = actor.getRole();
        if (role == Role.ADMIN || role == Role.SYSTEM_ADMIN || role == Role.LEADER || role == Role.REVIEWER) {
            return;
        }
        boolean isDrafter = document.getDrafter() != null && Objects.equals(document.getDrafter().getId(), actor.getId());
        boolean isSigner = document.getSigner() != null && Objects.equals(document.getSigner().getId(), actor.getId());
        if (isDrafter || isSigner) {
            return;
        }
        if (document.getStatus() == DocumentStatus.PUBLISHED) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập văn bản này");
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

    private byte[] loadDocumentPdfBytes(Document document) {
        return workingPdf.read(document);
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

    private record SignerTitleInfo(String title, String orgUnit) {}

    /**
     * Xác định Chức vụ (TITLE) và Phòng ban/Đơn vị (OU) của người ký theo nguyên tắc 2 lớp:
     * 1. Lớp 1: Quyết định bổ nhiệm thực tế trong position_assignments đang có hiệu lực.
     * 2. Lớp 2: Fallback theo vai trò Role hệ thống sang danh xưng tiếng Việt chuẩn hành chính.
     */
    private SignerTitleInfo resolveSignerTitleInfo(UserLogin user) {
        if (user == null) {
            return new SignerTitleInfo(null, null);
        }

        // Lớp 1: Kiểm tra bổ nhiệm thực tế trong bảng position_assignments
        if (positionAssignmentRepository != null && user.getId() != null) {
            try {
                List<PositionAssignment> assignments = positionAssignmentRepository.findActiveAssignments(user.getId(), Instant.now());
                if (assignments != null && !assignments.isEmpty()) {
                    PositionAssignment pa = assignments.get(0);
                    String posName = resolvePositionName(pa.getPositionCd());
                    String orgName = pa.getOrgNode() != null ? pa.getOrgNode().getNameVn() : null;
                    return new SignerTitleInfo(posName, orgName);
                }
            } catch (Exception ex) {
                log.warn("Lỗi khi tìm kiếm quyết định bổ nhiệm cho user {}: {}", user.getId(), ex.getMessage());
            }
        }

        // Lớp 2: Fallback sang danh xưng tiếng Việt chuẩn theo Role hệ thống
        Role role = user.getRole();
        if (role != null) {
            return switch (role) {
                case SYSTEM_ADMIN -> new SignerTitleInfo("Quản trị viên hệ thống", "Ban Quản trị");
                case ADMIN -> new SignerTitleInfo("Quản trị viên", "Ban Quản trị");
                case LEADER -> new SignerTitleInfo("Lãnh đạo đơn vị", "Ban Giám hiệu");
                case REVIEWER -> new SignerTitleInfo("Cán bộ thẩm định", "Phòng ban chuyên môn");
                case USER -> new SignerTitleInfo("Chuyên viên", "Trường Đại học Điện Lực");
                case CLERK -> new SignerTitleInfo("Cán bộ văn thư", "Phòng Hành chính - Tổng hợp");
            };
        }

        return new SignerTitleInfo(null, null);
    }

    private String resolvePositionName(String positionCd) {
        if (positionCd == null) return null;
        return switch (positionCd) {
            case "HT" -> "Hiệu trưởng";
            case "PHT" -> "Phó Hiệu trưởng";
            case "TK" -> "Trưởng khoa";
            case "PK" -> "Phó Trưởng khoa";
            case "TP" -> "Trưởng phòng";
            case "PP" -> "Phó Trưởng phòng";
            case "CV" -> "Chuyên viên";
            case "GV" -> "Giảng viên";
            case "VT" -> "Cán bộ văn thư";
            default -> positionCd;
        };
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
                .assignedOrgNodeName(log.getAssignedOrgNode() != null   ? log.getAssignedOrgNode().getNameVn() : null)
                .signatureHash(log.getSignatureHash())
                .createdDate(log.getCreatedDate())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = getCurrentActor();
        validateDocumentReadAccess(document, actor);

        List<DocumentWorkflowLog> logs = documentWorkflowLogRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        if (logs == null) {
            logs = java.util.Collections.emptyList();
        }
        return logs.stream()
                .map(log -> WorkflowTimelineResponse.builder()
                        .id(log.getId())
                        .documentId(log.getDocument() != null ? log.getDocument().getId() : null)
                        .actorUsername(log.getActor() != null ? log.getActor().getUsername() : null)
                        .actorRole(log.getActor() != null && log.getActor().getRole() != null
                        ? log.getActor().getRole().name()
                        : null)
                        .action(log.getAction() != null ? log.getAction().name() : null)
                        .comment(log.getComment())
                        .createdAt(log.getCreatedDate() != null ? LocalDateTime.ofInstant(log.getCreatedDate(), ZONE_VN) : null)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId, org.springframework.security.core.Authentication authentication) {
        verifyUserCanAccessDocument(documentId, authentication);
        return getDocumentTimeline(documentId);
    }

    @Override
    public void verifyUserCanAccessDocument(Long documentId, org.springframework.security.core.Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng");
        }

        boolean isAdmin = authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ADMIN")
                            || a.getAuthority().equals("ROLE_SYSTEM_ADMIN")
                            || a.getAuthority().equals("SYSTEM_ADMIN")
                            || a.getAuthority().equals("ROLE_CLERK")
                            || a.getAuthority().equals("CLERK"));
        if (isAdmin) {
            return;
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new vn.edu.epu.eoffice_be.common.exception.BusinessException(
                        vn.edu.epu.eoffice_be.common.enums.ErrorCode.DOCUMENT_NOT_FOUND,
                        "Không tìm thấy văn bản: " + documentId));

        if (doc.getStatus() == DocumentStatus.PUBLISHED) {
            return;
        }

        Long userId = extractUserId(authentication);
        Long creatorId = doc.getCreatorUser() != null ? doc.getCreatorUser().getId() : null;
        if (userId != null && userId.equals(creatorId)) {
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem timeline của tài liệu này");
    }

    Long extractUserId(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof vn.edu.epu.eoffice_be.security.UserIdAwarePrincipal userAware) {
            return userAware.getUserId();
        }

        try {
            java.lang.reflect.Method m = principal.getClass().getMethod("getUserId");
            Object res = m.invoke(principal);
            if (res instanceof Number num) {
                return num.longValue();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PendingDocumentResponse> getPendingDocuments(Pageable pageable) {
        Page<Document> page = documentRepository.findByStatusInAndDeleteFlgFalse(PENDING_STATUSES, pageable);
        if (page == null) {
            return Page.empty(pageable);
        }
        return page.map(doc -> PendingDocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .currentHolder(resolveCurrentHolder(doc.getStatus()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingDocumentResponse> getPendingDocuments() {
        List<Document> list = documentRepository.findByStatusInAndDeleteFlgFalse(PENDING_STATUSES);
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
                .currentHolder(resolveCurrentHolder(doc.getStatus()))
                .build()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PendingDocumentResponse> getPendingSignDocuments(Pageable pageable) {
        // LEADER và ADMIN xử lý văn bản của toàn bộ hệ thống → không lọc theo người tạo.
        // Các role khác chỉ thấy văn bản của mình (Data Isolation).
        UserLogin currentActor = getCurrentActor();
        boolean isPrivileged = currentActor.getRole() == Role.LEADER
                || currentActor.getRole() == Role.ADMIN
                || currentActor.getRole() == Role.SYSTEM_ADMIN;
        Long filterUserId = isPrivileged ? null : currentActor.getId();
        return documentRepository
                .searchDocuments(filterUserId, DocumentStatus.PENDING_SIGN, null, null, pageable)
                .map(doc -> PendingDocumentResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .status(doc.getStatus().name())
                        .currentHolder(resolveCurrentHolder(doc.getStatus()))
                        .updatedAt(doc.getUpdatedDate())
                        .pendingDays(computePendingDays(doc.getUpdatedDate()))
                        .isOverdue(computeIsOverdue(doc.getUpdatedDate()))
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PendingDocumentResponse> getPendingPublishDocuments(Pageable pageable) {
        // LEADER, ADMIN và REVIEWER (văn thư) xử lý ban hành toàn hệ thống.
        // Các role khác chỉ thấy văn bản của mình (Data Isolation).
        UserLogin currentActor = getCurrentActor();
        boolean isPrivileged = currentActor.getRole() == Role.LEADER
                || currentActor.getRole() == Role.ADMIN
                || currentActor.getRole() == Role.SYSTEM_ADMIN
                || currentActor.getRole() == Role.CLERK
                || currentActor.getRole() == Role.REVIEWER;
        Long filterUserId = isPrivileged ? null : currentActor.getId();
        return documentRepository
                .searchDocuments(filterUserId, DocumentStatus.APPROVED, null, null, pageable)
                .map(doc -> PendingDocumentResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .status(doc.getStatus().name())
                        .currentHolder(resolveCurrentHolder(doc.getStatus()))
                        .updatedAt(doc.getUpdatedDate())
                        .pendingDays(computePendingDays(doc.getUpdatedDate()))
                        .isOverdue(computeIsOverdue(doc.getUpdatedDate()))
                        .build());
    }

    private long computePendingDays(java.time.Instant updatedAt) {
        if (updatedAt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(updatedAt, java.time.Instant.now());
    }

    private boolean computeIsOverdue(java.time.Instant updatedAt) {
        return computePendingDays(updatedAt) > 3;
    }

    private String resolveCurrentHolder(DocumentStatus status) {
        if (status == null) {
            return "Bộ phận xử lý văn bản";
        }
        return switch (status) {
            case DRAFT -> "Người soạn thảo";
            case IN_REVIEW -> "Cán bộ thẩm định / ký nháy";
            case PENDING_APPROVAL -> "Lãnh đạo phê duyệt";
            case PENDING_SIGN -> "Lãnh đạo ký số";
            case APPROVED -> "Lãnh đạo ký phát hành";
            case PUBLISHED -> "Văn bản đã ban hành";
            case NEEDS_REVISION -> "Người soạn thảo chỉnh sửa";
            case REJECTED -> "Đã từ chối";
            default -> "Bộ phận xử lý văn bản";
        };
    }

    @Override
    public void logAction(Long documentId, String username, String role, String action, String comment) {
        if (action == null || action.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Hành động (action) không được để trống");
        }
        WorkflowAction act;
        try {
            act = WorkflowAction.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Hành động (action) không hợp lệ: " + action);
        }

        Document doc = null;
        if (documentRepository != null && documentId != null) {
            doc = documentRepository.findById(documentId).orElse(null);
        }
        if (doc == null && documentId != null) {
            doc = Document.builder().id(documentId).build();
        }

        UserLogin actor = null;
        if (userLoginRepository != null && username != null && !username.isBlank()) {
            actor = userLoginRepository.findByUsernameAndDeleteFlgFalse(username).orElse(null);
        }
        if (actor == null) {
            try {
                actor = getCurrentActor();
            } catch (Exception ignored) {
            }
        }
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Không thể xác định người thực hiện hành động (actor)");
        }

        DocumentStatus currentStatus = (doc != null && doc.getStatus() != null)
                ? doc.getStatus()
                : DocumentStatus.DRAFT;

        DocumentStatus toStatus = currentStatus;
        if (workflowEngine != null) {
            try {
                toStatus = workflowEngine.getNextStatus(currentStatus, act);
            } catch (BusinessException ex) {
                // Ngoại lệ nghiệp vụ mong đợi: hành động không làm thay đổi trạng thái trong ma trận
                log.warn("Hành động '{}' không làm thay đổi trạng thái từ '{}'. Giữ nguyên trạng thái hiện tại. Lý do: {}",
                        act, currentStatus, ex.getMessage());
                toStatus = currentStatus;
            } catch (Exception ex) {
                // Ngoại lệ hệ thống không mong đợi: ghi log error để truy vết
                log.error("Lỗi không mong muốn khi tính trạng thái tiếp theo cho hành động '{}' từ trạng thái '{}'",
                        act, currentStatus, ex);
                toStatus = currentStatus;
            }
        }
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .document(doc)
                .actor(actor)
                .action(act)
                .fromStatus(currentStatus)
                .toStatus(toStatus)
                .comment(comment)
                .build();
        documentWorkflowLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentSignatureVerifyResponse verifyDocumentSignatures(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = getCurrentActor();
        validateDocumentReadAccess(document, actor);
        byte[] pdfBytes = loadVerificationPdf(document);

        if (pdfSignatureVerifier == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Dịch vụ kiểm định chữ ký số (PdfSignatureVerifier) chưa được khởi tạo.");
        }

        DocumentSignatureVerifyResponse response = pdfSignatureVerifier.verify(pdfBytes);
        response.setDocumentId(document.getId());
        response.setDocumentTitle(document.getTitle());
        return response;
    }

    private byte[] loadVerificationPdf(Document document) {
        DocumentStatus status = document.getStatus();
        if (status == DocumentStatus.PUBLISHED || status == DocumentStatus.ISSUED || status == DocumentStatus.REVOKED) {
            return documentPdfStorageService.readPdf(document.getOfficialFilePath());
        }
        return loadDocumentPdfBytes(document);
    }

    @Override
    @Transactional
    public DocumentDetailResponse revokeDocument(Long documentId, DocumentRevokeRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // 1. Kiểm tra thẩm quyền thu hồi văn bản (chỉ Lãnh đạo hoặc Quản trị viên)
        validateActorAuthorization(document, actor, WorkflowAction.REVOKE);

        // 2. Chuyển trạng thái sang REVOKED và ghi vết kiểm toán
        workflowEngine.executeTransition(
                document,
                WorkflowAction.REVOKE,
                actor,
                request.getComment(),
                null,
                null,
                null
        );

        // 3. Tự động đồng bộ số Serial của chữ ký ban hành vào danh sách thu hồi CRL (revoked.json)
        try {
            byte[] pdfBytes = loadVerificationPdf(document);
            if (pdfBytes != null && pdfBytes.length > 0 && pdfSignatureVerifier != null) {
                DocumentSignatureVerifyResponse verifyResp = pdfSignatureVerifier.verify(pdfBytes);
                if (verifyResp != null && verifyResp.getSignatures() != null) {
                    for (SignatureItemResponse item : verifyResp.getSignatures()) {
                        if (item.getSerialHex() != null && !item.getSerialHex().isBlank()) {
                            pdfSignatureVerifier.revokeSerial(item.getSerialHex());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tự động đồng bộ danh sách thu hồi CRL cho văn bản id={}: {}", documentId, e.getMessage());
        }

        return mapToDetailResponse(document);
    }
    private void saveDistributions(Long documentId, DistributionScope scope, List<Long> recipientOrgIds) {
        if (scope == DistributionScope.ALL) {
            distributionRepository.save(DocumentDistribution.builder()
                    .documentId(documentId)
                    .targetType("ALL")
                    .targetId(null)
                    .isRead(false)
                    .build());
            return;
        }

        List<DocumentDistribution> distributions = recipientOrgIds.stream()
                .map(orgId -> DocumentDistribution.builder()
                        .documentId(documentId)
                        .targetType("ORG")
                        .targetId(orgId)
                        .isRead(false)
                        .build())
                .toList();
        distributionRepository.saveAll(distributions);
    }

    private List<Long> validateRecipients(DocumentPublishRequest request) {
        if (request == null || request.getDistributionScope() == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Phải chọn phạm vi phát hành"
            );
        }

        DistributionScope scope = request.getDistributionScope();
        List<Long> supplied = request.getRecipientOrgIds();
        boolean hasSupplied = supplied != null && !supplied.isEmpty();

        // 1. Toàn trường: không kèm danh sách đơn vị.
        if (scope == DistributionScope.ALL) {
            if (hasSupplied) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "Phát hành toàn trường thì không chọn thêm đơn vị nhận"
                );
            }
            return List.of();
        }

        // 2. Một hoặc nhiều đơn vị: bắt buộc có danh sách.
        if (!hasSupplied) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Phải chọn đơn vị nhận"
            );
        }

        if (supplied.size() > MAX_RECIPIENTS) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Danh sách nơi nhận không được vượt quá "
                            + MAX_RECIPIENTS + " phần tử"
            );
        }

        if (supplied.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "ID đơn vị nhận không hợp lệ"
            );
        }

        // Loại ID trùng trước khi đếm số đơn vị.
        List<Long> recipientIds =
                new ArrayList<>(new LinkedHashSet<>(supplied));

        // 3. Chọn một đơn vị: phải có đúng một ID khác nhau.
        if (scope == DistributionScope.SINGLE_ORG
                && recipientIds.size() != 1) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Phải chọn đúng một đơn vị nhận"
            );
        }

        // 4. Chọn nhiều đơn vị: phải có ít nhất hai ID khác nhau.
        if (scope == DistributionScope.MULTIPLE_ORGS
                && recipientIds.size() < 2) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Phải chọn ít nhất hai đơn vị nhận khác nhau"
            );
        }

        // 5. Kiểm tra các đơn vị tồn tại và chưa bị xóa.
        Set<Long> foundIds = orgNodeRepository
                .findByIdInAndDeleteFlgFalse(recipientIds)
                .stream()
                .map(OrgNode::getId)
                .collect(Collectors.toSet());

        List<Long> missing = recipientIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();

        if (!missing.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.ORG_NODE_NOT_FOUND,
                    "Không tìm thấy đơn vị nhận: " + missing
            );
        }

        return recipientIds;
    }

}

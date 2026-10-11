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
import vn.edu.epu.eoffice_be.common.enums.Role;
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
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.service.DocumentBookService;

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
    private final DocumentBookService documentBookService;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final vn.edu.epu.eoffice_be.storage.FileStorageService unifiedStorageService;

    public DocumentWorkflowServiceImpl(DocumentRepository documentRepository,
                                       DocumentWorkflowLogRepository documentWorkflowLogRepository) {
        this(documentRepository, documentWorkflowLogRepository, null, null, null, null, null, null, null, null, null);
    }

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final List<DocumentStatus> PENDING_STATUSES = List.of(
            DocumentStatus.PENDING_APPROVAL,
            DocumentStatus.WAITING_APPROVAL,
            DocumentStatus.IN_REVIEW,
            DocumentStatus.PENDING_SIGN,
            DocumentStatus.SUBMITTED
    );

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
        if (document.getStatus() == DocumentStatus.SUBMITTED
                || document.getStatus() == DocumentStatus.IN_REVIEW
                || document.getStatus() == DocumentStatus.PENDING_REVIEW) {
            action = WorkflowAction.REVIEW_APPROVE;
        } else if (document.getStatus() == DocumentStatus.LEADER_REVIEW
                || document.getStatus() == DocumentStatus.PENDING_APPROVAL
                || document.getStatus() == DocumentStatus.WAITING_APPROVAL) {
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
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request) {
        UserLogin actor = getCurrentActor();
        Document document = findDocumentOrThrow(documentId);

        // Kiểm tra thẩm quyền ký nháy TRƯỚC KHI xử lý file PDF nhằm tránh lãng phí tài nguyên CPU/IO
        validateActorAuthorization(document, actor, WorkflowAction.INITIAL_SIGN);

        // 1. Kiểm tra file PDF nguồn: nếu chưa có file đính kèm chính thì báo lỗi
        ensureDocumentPdfExists(document);

        byte[] currentPdfBytes = loadDocumentPdfBytes(document);

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

        // Nếu văn bản chưa có người ký được gán trước đó, gán actor làm signer tại thời điểm ký phát hành
        if (document.getSigner() == null) {
            document.setSigner(actor);
        }

        // Kiểm tra thẩm quyền ký phát hành TRƯỚC KHI xử lý ký số PKI nhằm tránh lãng phí tài nguyên
        validateActorAuthorization(document, actor, WorkflowAction.SIGN_AND_PUBLISH);

        // Kiểm tra file PDF nguồn TRƯỚC KHI cấp số và ký số: nếu chưa có file đính kèm chính thì báo lỗi
        ensureDocumentPdfExists(document);

        // 2. Cấp số chính thức qua Sổ văn bản (Pessimistic Write Lock)
        String officialNumber;
        if (request.getBookId() != null && documentBookService != null) {
            AssignDocumentNumberRequest assignReq = new AssignDocumentNumberRequest();
            assignReq.setBookId(request.getBookId());
            assignReq.setDocumentId(documentId);
            assignReq.setEffectiveDate(LocalDate.ofInstant(effectiveDate, ZONE_VN));

            AssignDocumentNumberResponse numberResp = documentBookService.assignNumber(assignReq);
            officialNumber = numberResp.getDocumentNumber();
        } else if (request.getOfficialNumber() != null && !request.getOfficialNumber().isBlank()) {
            officialNumber = request.getOfficialNumber();
        } else if (document.getOfficialNumber() != null && !document.getOfficialNumber().isBlank()) {
            // Tự động kế thừa số hiệu đã được cấp từ sổ văn bản ở bước Chờ ban hành (APPROVED)
            officialNumber = document.getOfficialNumber();
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Bắt buộc chọn sổ văn bản để cấp số chính thức");
        }

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

        // 4. Ký số điện tử PKI X.509 PAdES và đặt cờ niêm phong CERTIFIED_NO_CHANGES_ALLOWED
        String signerDisplayName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(actor.getId())
                .map(UserInfo::getFullName)
                .orElse(actor.getUsername());

        byte[] certifiedPdfBytes = pdfProcessingService.signAndCertifyPdf(
                currentPdfBytes,
                signerDisplayName,
                "Ban hanh van ban: " + officialNumber
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

    private void ensureDocumentPdfExists(Document document) {
        if (document.getPrimaryFilePath() != null && !document.getPrimaryFilePath().isBlank()) {
            return;
        }
        if (documentAttachmentRepository != null) {
            List<DocumentAttachment> allAttachments = documentAttachmentRepository
                    .findByDocumentIdAndDeleteFlgFalse(document.getId());

            if (!allAttachments.isEmpty()) {
                // 1) Kiểm tra xem có tệp đính kèm chính (isMainDocument == true) hay không
                Optional<DocumentAttachment> mainAttachmentOpt = allAttachments.stream()
                        .filter(att -> att != null && Boolean.TRUE.equals(att.isMainDocument()))
                        .findFirst();

                if (mainAttachmentOpt.isPresent()) {
                    DocumentAttachment mainAtt = mainAttachmentOpt.get();
                    if (!isPdfAttachment(mainAtt)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "Tệp đính kèm chính của văn bản không phải định dạng PDF. Ký nháy/ký số yêu cầu tệp chính phải là định dạng PDF.");
                    }
                    return;
                }

                // 2) Nếu không có tệp chính được chỉ định, kiểm tra có ít nhất một tệp PDF hợp lệ
                boolean hasPdfAttachment = allAttachments.stream().anyMatch(this::isPdfAttachment);
                if (!hasPdfAttachment) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "Văn bản đã có tệp đính kèm nhưng không phải định dạng PDF. Ký nháy/ký số chỉ hỗ trợ tệp định dạng PDF.");
                }
                return;
            }
        }
        throw new BusinessException(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING);
    }

    private boolean isPdfAttachment(DocumentAttachment att) {
        if (att == null) {
            return false;
        }
        return "PDF".equalsIgnoreCase(att.getFileExtension()) ||
                (att.getOriginalFileName() != null && att.getOriginalFileName().toLowerCase().endsWith(".pdf"));
    }

    private byte[] loadDocumentPdfBytes(Document document) {
        // 1. Thử nạp qua fileStorageService nếu primaryFilePath đã có (ví dụ: đã qua bước ký nháy)
        if (document.getPrimaryFilePath() != null && !document.getPrimaryFilePath().isBlank()) {
            try {
                byte[] bytes = fileStorageService.loadFileBytes(document.getPrimaryFilePath());
                if (bytes != null && bytes.length > 0) {
                    return bytes;
                }
            } catch (Exception e) {
                log.warn("Không thể nạp file từ primaryFilePath '{}': {}", document.getPrimaryFilePath(), e.getMessage());
            }
        }

        // 2. Thử nạp từ tệp đính kèm DocumentAttachment qua unifiedStorageService (Telegram/filesystem)
        if (documentAttachmentRepository != null) {
            List<DocumentAttachment> allAttachments = documentAttachmentRepository
                    .findByDocumentIdAndDeleteFlgFalse(document.getId());

            if (!allAttachments.isEmpty()) {
                // 1) Kiểm tra xem có tệp đính kèm chính (isMainDocument == true) hay không
                Optional<DocumentAttachment> mainAttachmentOpt = allAttachments.stream()
                        .filter(att -> att != null && Boolean.TRUE.equals(att.isMainDocument()))
                        .findFirst();

                DocumentAttachment targetAttachment;
                if (mainAttachmentOpt.isPresent()) {
                    DocumentAttachment mainAtt = mainAttachmentOpt.get();
                    if (!isPdfAttachment(mainAtt)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "Tệp đính kèm chính của văn bản không phải định dạng PDF. Ký nháy/ký số yêu cầu tệp chính phải là định dạng PDF.");
                    }
                    targetAttachment = mainAtt;
                } else {
                    // 2) Nếu không có tệp chính được chỉ định, tìm tệp PDF đầu tiên trong danh sách
                    targetAttachment = allAttachments.stream()
                            .filter(this::isPdfAttachment)
                            .findFirst()
                            .orElse(null);

                    if (targetAttachment == null) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST,
                                "Văn bản đã có tệp đính kèm nhưng không phải định dạng PDF. Ký nháy/ký số chỉ hỗ trợ tệp định dạng PDF.");
                    }
                }

                if (unifiedStorageService != null) {
                    try {
                        org.springframework.core.io.Resource res = unifiedStorageService.loadAsResource(
                                targetAttachment.getStoragePath(), targetAttachment.getStorageFileName()
                        );
                        if (res != null && res.exists() && res.isReadable()) {
                            try (java.io.InputStream is = res.getInputStream()) {
                                byte[] bytes = is.readAllBytes();
                                if (bytes.length > 0) {
                                    return bytes;
                                }
                            }
                        } else {
                            log.error("Tệp đính kèm PDF id={} (storagePath={}, storageFileName={}) không tồn tại hoặc không thể đọc trên hệ thống lưu trữ",
                                    targetAttachment.getId(), targetAttachment.getStoragePath(), targetAttachment.getStorageFileName());
                        }
                    } catch (Exception e) {
                        log.error("Lỗi khi đọc tệp đính kèm PDF id={}: {}", targetAttachment.getId(), e.getMessage(), e);
                    }
                }

                // Trường hợp 2: Có tệp đính kèm PDF hợp lệ nhưng không thể nạp nội dung từ storage
                throw new BusinessException(ErrorCode.STORAGE_ERROR,
                        "Không thể tải nội dung tệp PDF đính kèm từ hệ thống lưu trữ. Vui lòng thử lại sau.");
            }
        }

        throw new BusinessException(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING);
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
                            || a.getAuthority().equals("SYSTEM_ADMIN"));
        if (isAdmin) {
            return;
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new vn.edu.epu.eoffice_be.common.exception.BusinessException(
                        vn.edu.epu.eoffice_be.common.enums.ErrorCode.DOCUMENT_NOT_FOUND,
                        "Không tìm thấy văn bản: " + documentId));

        if (doc.getStatus() == vn.edu.epu.eoffice_be.common.enums.DocumentStatus.PUBLISHED
                || doc.getStatus() == vn.edu.epu.eoffice_be.common.enums.DocumentStatus.ISSUED) {
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
            case SUBMITTED -> "Văn thư tiếp nhận";
            case PENDING_REVIEW, IN_REVIEW -> "Chuyên viên thẩm định";
            case PENDING_APPROVAL, WAITING_APPROVAL, LEADER_REVIEW -> "Lãnh đạo phê duyệt";
            case PENDING_SIGN -> "Lãnh đạo ký số";
            case APPROVED -> "Lãnh đạo ký ban hành";
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
}

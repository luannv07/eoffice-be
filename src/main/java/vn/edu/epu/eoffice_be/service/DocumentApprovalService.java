package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentProcessAction;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.SubmissionRoute;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.dto.response.SubmissionOptionsResponse;
import vn.edu.epu.eoffice_be.event.DocumentQueuedForClerksEvent;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.RecallApprovalRequest;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.dto.response.EligibleApproverResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentAttachment;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;
import vn.edu.epu.eoffice_be.entity.DocumentProcessLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.PositionPermission;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.event.DocumentSubmittedEvent;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentApprovalService {

    private final DocumentRepository documentRepository;
    private final DocumentAttachmentRepository documentAttachmentRepository;
    private final DocumentApprovalRequestRepository documentApprovalRequestRepository;
    private final DocumentProcessLogRepository documentProcessLogRepository;
    private final UserLoginRepository userLoginRepository;
    private final UserInfoRepository userInfoRepository;
    private final PositionAssignmentRepository positionAssignmentRepository;
    private final PositionPermissionRepository positionPermissionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentDraftService documentDraftService;
    private final DocumentPdfStorageService documentPdfStorageService;
    private final vn.edu.epu.eoffice_be.storage.FileStorageService unifiedStorageService;
    private final PdfSigner pdfSigner;
    private final SubmissionRoutingService submissionRoutingService;
    private final WorkflowEngine workflowEngine;
    private final DraftUnitResolver units;

    /**
     * Lấy danh sách cán bộ có thẩm quyền duyệt văn bản (theo đơn vị hoặc toàn hệ thống)
     */
    @Transactional(readOnly = true)
    public SubmissionOptionsResponse getSubmissionOptions(Long documentId, Long userId) {
        UserLogin sender = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        return submissionRoutingService.options(document, sender, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<EligibleApproverResponse> getEligibleApprovers(Long documentId, Long userId) {
        UserLogin sender = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        SubmissionRoutingService.assertOwnership(document, sender);
        if (document.getStatus() == DocumentStatus.DRAFT) {
            return submissionRoutingService.options(document, sender, Instant.now()).routes().stream()
                    .flatMap(route -> route.approvers().stream())
                    .collect(Collectors.toMap(EligibleApproverResponse::getUserId, value -> value,
                            (first, other) -> first, java.util.LinkedHashMap::new))
                    .values().stream().toList();
        }
        return legacyEligibleApprovers(document);
    }

    private List<EligibleApproverResponse> legacyEligibleApprovers(Document document) {
        Set<String> approverPositions = resolveApproverPositions();
        Instant now = Instant.now();
        List<PositionAssignment> assignments = positionAssignmentRepository
                .findActiveAssignmentsByPositionCds(approverPositions, now);
        Set<Long> allowedOrgNodeIds = resolveAllowedOrgNodeIds(document);

        List<EligibleApproverResponse> responses = new ArrayList<>();
        Set<Long> addedUserIds = new HashSet<>();

        for (PositionAssignment pa : assignments) {
            if (isAssignmentEligible(pa, addedUserIds, allowedOrgNodeIds)) {
                responses.add(buildApproverResponse(pa));
                addedUserIds.add(pa.getUserLogin().getId());
            }
        }

        if (responses.isEmpty()) {
            responses.addAll(buildFallbackAdminResponses(document));
        }

        return responses;
    }

    private Set<String> resolveApproverPositions() {
        Set<String> leaderCds = Set.of("HT", "PHT", "TK", "TP", "PK", "PP");
        Set<String> positions = positionPermissionRepository
                .findByPositionCdInAndDeleteFlgFalse(leaderCds).stream()
                .map(PositionPermission::getPositionCd)
                .collect(Collectors.toSet());
        return positions.isEmpty() ? new HashSet<>(leaderCds) : positions;
    }

    private boolean isAssignmentEligible(PositionAssignment pa, Set<Long> addedUserIds, Set<Long> allowedOrgNodeIds) {
        if (pa == null || pa.isDeleteFlg() || pa.getActualDismissalDate() != null) {
            return false;
        }
        UserLogin approverUser = pa.getUserLogin();
        if (approverUser == null || approverUser.isDeleteFlg() || addedUserIds.contains(approverUser.getId())) {
            return false;
        }
        OrgNode node = pa.getOrgNode();
        return allowedOrgNodeIds.isEmpty() || node == null || allowedOrgNodeIds.contains(node.getId());
    }

    private EligibleApproverResponse buildApproverResponse(PositionAssignment pa) {
        UserLogin approverUser = pa.getUserLogin();
        OrgNode node = pa.getOrgNode();
        Optional<UserInfo> infoOpt = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(approverUser.getId());
        String fullName = infoOpt.map(UserInfo::getFullName).orElse(approverUser.getUsername());

        return EligibleApproverResponse.builder()
                .userId(approverUser.getId())
                .username(approverUser.getUsername())
                .fullName(fullName)
                .email(approverUser.getEmail())
                .positionCd(pa.getPositionCd())
                .positionName(resolvePositionName(pa.getPositionCd()))
                .orgNodeId(node != null ? node.getId() : null)
                .orgNodeName(node != null ? node.getNameVn() : null)
                .build();
    }

    private List<EligibleApproverResponse> buildFallbackAdminResponses(Document document) {
        Long orgNodeId = document.getIssuingOrgNode() != null ? document.getIssuingOrgNode().getId() : null;
        String orgNodeName = document.getIssuingOrgNode() != null ? document.getIssuingOrgNode().getNameVn() : null;

        List<UserLogin> admins = userLoginRepository
                .findByRoleInAndDeleteFlgFalse(List.of(Role.ADMIN, Role.SYSTEM_ADMIN));
        List<EligibleApproverResponse> adminResponses = new ArrayList<>();
        for (UserLogin admin : admins) {
            Optional<UserInfo> infoOpt = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(admin.getId());
            adminResponses.add(EligibleApproverResponse.builder()
                    .userId(admin.getId())
                    .username(admin.getUsername())
                    .fullName(infoOpt.map(UserInfo::getFullName).orElse(admin.getUsername()))
                    .email(admin.getEmail())
                    .positionCd("ADMIN")
                    .positionName("Quản trị viên")
                    .orgNodeId(orgNodeId)
                    .orgNodeName(orgNodeName)
                    .build());
        }
        return adminResponses;
    }

    /**
     * UC11 - Luồng 2.1: Gửi văn bản để duyệt
     */
    @Transactional
    public DocumentResponse submitForApproval(Long documentId, SubmitApprovalRequest request, Long userId) {
        UserLogin sender = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Người soạn tự ký và gửi; role quản trị không cho phép gửi thay.
        if (document.getDrafter() == null || document.getDrafter().getId() == null
                || !document.getDrafter().getId().equals(sender.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền gửi duyệt văn bản này");
        }

        if (!"ACTIVE".equals(sender.getUserStatusCd())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // Preserve existing allowed statuses; terminal REJECTED policy is a separate change.
        
        if (document.getStatus() != DocumentStatus.DRAFT 
            && document.getStatus() != DocumentStatus.REJECTED 
            && document.getStatus() != DocumentStatus.NEEDS_REVISION) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Chỉ có thể gửi duyệt văn bản đang ở trạng thái Dự thảo hoặc Cần chỉnh sửa");
        }

        // Kiểm tra tính đầy đủ hồ sơ (PDF bước 3, 4):
        // 1. Phải có tiêu đề và trích yếu
        if (document.getTitle() == null || document.getTitle().isBlank() ||
                document.getSummary() == null || document.getSummary().isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_INCOMPLETE_DOSSIER,
                    "Hồ sơ chưa đầy đủ: Tiêu đề và trích yếu văn bản không được để trống");
        }

        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Thiếu thông tin gửi văn bản");
        Instant now = Instant.now();
        SubmissionRoutingService.Decision decision = null;
        UserLogin approver = null;
        WorkflowAction submissionAction = WorkflowAction.SUBMIT;
        if (document.getStatus() == DocumentStatus.DRAFT) {
            // Keep the existing NOT_FOUND contract before resolving an explicit recipient.
            if (request.getApproverUserId() != null) {
                approver = userLoginRepository.findByIdAndDeleteFlgFalse(request.getApproverUserId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người duyệt được chỉ định"));
            }
            decision = submissionRoutingService.resolve(document, sender, request, now);
            submissionAction = switch (decision.route()) {
                case REVIEW -> WorkflowAction.SUBMIT;
                case LEADER_APPROVAL -> WorkflowAction.SUBMIT_TO_LEADER;
                case CLERK_QUEUE -> WorkflowAction.SUBMIT_TO_CLERK_QUEUE;
            };
            workflowEngine.validateTransition(document, submissionAction, request.getSubmitNote());
            workflowEngine.validateActorPermission(document, sender, submissionAction);
            if (decision.approverUserId() != null && approver == null) {
                approver = userLoginRepository.findByIdAndDeleteFlgFalse(decision.approverUserId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người duyệt được chỉ định"));
            }
        } else if (request.getApproverUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Người duyệt không được để trống khi gửi lại");
        }
        boolean clerkQueue = decision != null && decision.route() == SubmissionRoute.CLERK_QUEUE;
        boolean isResubmission = document.getStatus() == DocumentStatus.NEEDS_REVISION
                || document.getStatus() == DocumentStatus.REJECTED;

        if (decision == null) {
            // Preserve resubmission policy until its own rework.
            approver = userLoginRepository.findByIdAndDeleteFlgFalse(request.getApproverUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người duyệt được chỉ định"));
            if (!"ACTIVE".equals(approver.getUserStatusCd()) || !isUserEligibleApprover(approver, document)) {
                throw new BusinessException(ErrorCode.APPROVER_NOT_ELIGIBLE,
                        "Người được chọn không có thẩm quyền duyệt văn bản này hoặc không thuộc đơn vị phù hợp");
            }
        }

        // Complete all dossier/recipient checks before signing or mutating submission data.
        byte[] pdfBytes = loadSubmissionPdf(document);
        if (pdfSigner == null) {
            throw new BusinessException(ErrorCode.SIGNATURE_PROCESS_ERROR, "Bộ ký xác nhận chưa khả dụng");
        }
        String signatureHash;
        String signerFullName = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(sender.getId())
                .map(UserInfo::getFullName)
                .orElse(sender.getUsername());
        SignerTitleInfo titleInfo = resolveSignerTitleInfo(sender);
        String signerDisplayName = (titleInfo.title() != null && !titleInfo.title().isBlank())
                ? signerFullName + " - " + titleInfo.title()
                : signerFullName;

        String signReason = (request.getSubmitNote() != null && !request.getSubmitNote().isBlank())
                ? request.getSubmitNote()
                : "Ký nháy văn bản (Người soạn thảo)";

        try {
            PdfSigner.SignResult signResult = pdfSigner.signBytes(
                    pdfBytes,
                    String.valueOf(sender.getId()),
                    signerDisplayName,
                    titleInfo.title(),
                    titleInfo.orgUnit(),
                    signReason
            );
            if (signResult == null || signResult.signedPdfBytes() == null
                    || signResult.signedPdfBytes().length == 0 || signResult.outPdfSha256() == null
                    || signResult.outPdfSha256().isBlank()) {
                throw new BusinessException(ErrorCode.SIGNATURE_PROCESS_ERROR, "Kết quả ký xác nhận không hợp lệ");
            }
            byte[] stampedPdfBytes = signResult.signedPdfBytes();
            String newPdfReference = documentPdfStorageService.storeSignedPdf(
                    stampedPdfBytes,
                    "initial_signed_" + document.getId() + ".pdf",
                    document.getId()
            );
            if (newPdfReference == null || newPdfReference.isBlank()) {
                throw new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_FAILED);
            }
            document.setPrimaryFilePath(newPdfReference);
            signatureHash = signResult.outPdfSha256();
            log.info("Đã tự động ký nháy lần 1 cho văn bản id={}, người ký={}, hash={}",
                    document.getId(), sender.getUsername(), signatureHash);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Lỗi khi tự động ký nháy văn bản id={}: {}", document.getId(), ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.SIGNATURE_PROCESS_ERROR,
                    "Lỗi khi tự động ký nháy văn bản: " + ex.getMessage());
        }

        // Lưu trạng thái nguồn trước khi đổi trạng thái (Issue 18)
        DocumentStatus prevStatus = document.getStatus();

        if (isResubmission) {
            document.setCurrentVersion(Math.max(document.getCurrentVersion(), 1) + 1);
        }
        if (decision != null && units.simulated(sender)) {
            document.setTestDocument(true);
            log.info("ADMIN_SIMULATION submit actor={} document={} unit={} route={} recipient={}",
                    sender.getId(), document.getId(), document.getOrgId(), decision.route(), decision.approverUserId());
        }
        document.setSubmittedAt(now);
        Document savedDocument;
        if (decision != null) {
            workflowEngine.executeTransition(document, submissionAction, sender,
                    clerkQueue ? "Hiệu trưởng xác nhận phê duyệt nội dung và chuyển hàng chờ văn thư. "
                            + (request.getSubmitNote() == null ? "" : request.getSubmitNote()) : request.getSubmitNote(),
                    null, null, signatureHash);
            savedDocument = document;
        } else {
            document.setStatus(DocumentStatus.IN_REVIEW);
            savedDocument = documentRepository.save(document);
        }

        if (!clerkQueue) {
            DocumentApprovalRequest approvalRequest = DocumentApprovalRequest.builder()
                    .document(savedDocument).senderUser(sender).approverUser(approver)
                    .status(ApprovalRequestStatus.PENDING).submitNote(request.getSubmitNote())
                    .submittedAt(now).build();
            documentApprovalRequestRepository.save(approvalRequest);
        }

        // Ghi nhật ký tiến trình với fromStatus chính xác từ trạng thái nguồn (Issue 18)
        String destination = clerkQueue ? "hàng chờ văn thư; Hiệu trưởng xác nhận phê duyệt nội dung"
                : "cán bộ duyệt: " + approver.getUsername();
        String logNote = "Gửi văn bản tới " + destination +
                (request.getSubmitNote() != null ? ". Ghi chú: " + request.getSubmitNote() : "") +
                (signatureHash != null ? " [Đã ký nháy tự động, SHA256: " + signatureHash + "]" : "");

        DocumentProcessLog processLog = DocumentProcessLog.builder()
                .document(savedDocument)
                .actorUser(sender)
                .action(DocumentProcessAction.SUBMIT_APPROVAL)
                .fromStatus(prevStatus != null ? prevStatus.name() : DocumentStatus.DRAFT.name())
                .toStatus(savedDocument.getStatus().name())
                .note(logNote)
                .build();
        documentProcessLogRepository.save(processLog);

        // Bắn sự kiện gửi thông báo cho người duyệt
        String cleanTitle = savedDocument.getTitle() != null
                ? savedDocument.getTitle().replace('\n', '_').replace('\r', '_')
                : "";
        String cleanSubmitNote = request.getSubmitNote() != null
                ? request.getSubmitNote().replace('\n', '_').replace('\r', '_')
                : null;
        if (clerkQueue) {
            eventPublisher.publishEvent(new DocumentQueuedForClerksEvent(
                    savedDocument.getId(), sender.getId(), cleanTitle, cleanSubmitNote));
        } else {
            eventPublisher.publishEvent(new DocumentSubmittedEvent(
                    this, savedDocument.getId(), sender.getId(), approver.getId(), cleanTitle, cleanSubmitNote));
        }
        log.info("Người dùng id={} đã gửi văn bản id={} theo tuyến {}", sender.getId(),
                savedDocument.getId(), decision == null ? "LEGACY_RESUBMISSION" : decision.route());

        return documentDraftService.mapToDocumentResponse(savedDocument);
    }

    /**
     * UC11 - Luồng 2.2: Thu hồi yêu cầu duyệt
     */
    @Transactional
    public DocumentResponse recallApproval(Long documentId, RecallApprovalRequest request, Long userId) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền: chỉ người soạn được thu hồi yêu cầu duyệt
        UserLogin drafter = document.getDrafter();
        boolean isDrafter = drafter != null
                && drafter.getId() != null
                && drafter.getId().equals(userId);
        if (!isDrafter) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thu hồi yêu cầu duyệt của văn bản này");
        }

        // Kiểm tra trạng thái: văn bản phải đang ở trạng thái WAITING_APPROVAL
        if (document.getStatus() != DocumentStatus.IN_REVIEW)  {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_STATUS,
                    "Chỉ có thể thu hồi văn bản đang ở trạng thái Chờ duyệt");
        }

        // Kiểm tra người duyệt đã xử lý chưa (PDF bước 2, 3)
        Optional<DocumentApprovalRequest> latestRequestOpt = documentApprovalRequestRepository
                .findTopByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(documentId);

        if (latestRequestOpt.isPresent()) {
            DocumentApprovalRequest latestRequest = latestRequestOpt.get();
            if (latestRequest.getStatus() != ApprovalRequestStatus.PENDING) {
                throw new BusinessException(ErrorCode.DOCUMENT_CANNOT_RECALL,
                        "Không thể thu hồi văn bản do người duyệt đã xử lý yêu cầu (" + latestRequest.getStatus().getDescription() + ")");
            }

            // Đánh dấu yêu cầu duyệt đã bị thu hồi
            latestRequest.setStatus(ApprovalRequestStatus.RECALLED);
            latestRequest.setProcessedAt(Instant.now());
            documentApprovalRequestRepository.save(latestRequest);
        }

        // Chuyển trạng thái văn bản về lại DRAFT (PDF bước 4)
        documentAttachmentRepository.retireCurrentMain(documentId);
        document.setPrimaryFilePath(null);
        document.setStatus(DocumentStatus.DRAFT);
        Document savedDocument = documentRepository.save(document);

        // Ghi nhật ký thu hồi (PDF bước 5)
        String recallReason = request != null && request.getRecallReason() != null && !request.getRecallReason().isBlank()
                ? request.getRecallReason() : "Thu hồi để chỉnh sửa lại";

        DocumentProcessLog processLog = DocumentProcessLog.builder()
                .document(savedDocument)
                .actorUser(user)
                .action(DocumentProcessAction.RECALL_APPROVAL)
                .fromStatus(DocumentStatus.IN_REVIEW.name())
                .toStatus(DocumentStatus.DRAFT.name())
                .note("Thu hồi yêu cầu duyệt. Lý do: " + recallReason)
                .build();
        documentProcessLogRepository.save(processLog);

        log.info("Người dùng id={} đã thu hồi yêu cầu duyệt văn bản id={}", user.getId(), savedDocument.getId());

        return documentDraftService.mapToDocumentResponse(savedDocument);
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
            default -> positionCd;
        };
    }

    public boolean isUserEligibleApprover(UserLogin user) {
        return isUserEligibleApprover(user, null);
    }

    public boolean isUserEligibleApprover(UserLogin user, Document document) {
        if (user == null || user.isDeleteFlg()) {
            return false;
        }
        if (DocumentAdminPolicy.isAdmin(user)) return false;

        Set<String> leaderCds = Set.of("HT", "PHT", "TK", "TP", "PK", "PP");
        Set<String> approverPositions = positionPermissionRepository
                .findByPositionCdInAndDeleteFlgFalse(leaderCds).stream()
                .map(PositionPermission::getPositionCd)
                .collect(Collectors.toSet());
        if (approverPositions.isEmpty()) {
            approverPositions.addAll(leaderCds);
        }

        Set<Long> allowedOrgNodeIds = resolveAllowedOrgNodeIds(document);

        Instant now = Instant.now();
        return positionAssignmentRepository.findByUserLoginIdAndDeleteFlgFalse(user.getId()).stream()
                .filter(pa -> pa.getActualDismissalDate() == null)
                .filter(pa -> pa.getEffectiveFrom() == null || !pa.getEffectiveFrom().isAfter(now))
                .filter(pa -> pa.getEffectiveTo() == null || !pa.getEffectiveTo().isBefore(now))
                .filter(pa -> approverPositions.contains(pa.getPositionCd()))
                .anyMatch(pa -> {
                    if (allowedOrgNodeIds.isEmpty()) {
                        return true;
                    }
                    OrgNode node = pa.getOrgNode();
                    return node == null || allowedOrgNodeIds.contains(node.getId());
                });
    }

    private Set<Long> resolveAllowedOrgNodeIds(Document document) {
        Set<Long> allowed = new HashSet<>();
        if (document != null && document.getIssuingOrgNode() != null) {
            OrgNode current = document.getIssuingOrgNode();
            int depth = 0;
            final int maxDepth = 100;
            while (current != null && depth < maxDepth) {
                depth++;
                if (current.getId() != null && !allowed.add(current.getId())) {
                    break; // Phát hiện chu trình (cycle detected), dừng duyệt
                }
                current = current.getParent();
            }
        }
        return allowed;
    }

    private record SignerTitleInfo(String title, String orgUnit) {}

    private SignerTitleInfo resolveSignerTitleInfo(UserLogin user) {
        if (user == null) {
            return new SignerTitleInfo(null, null);
        }

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

    /** Submission signs the current uploaded main, never a previous round's signed PDF. */
    private byte[] loadSubmissionPdf(Document document) {
        List<DocumentAttachment> mains = documentAttachmentRepository
                .findByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(document.getId()).stream()
                .filter(a -> a != null && !a.isDeleteFlg() && a.isMainDocument()).toList();
        if (mains.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_PRIMARY_FILE_MISSING,
                    "Vui lòng tải lên tệp PDF chính mới để gửi duyệt.");
        }
        if (mains.size() != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_INCOMPLETE_DOSSIER,
                    "Hồ sơ có nhiều tệp chính. Vui lòng kiểm tra lại trước khi gửi duyệt.");
        }
        DocumentAttachment main = mains.getFirst();
        if (!isPdfAttachment(main)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Tệp chính phải là PDF");
        }
        if (unifiedStorageService == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        }
        try {
            org.springframework.core.io.Resource resource = unifiedStorageService.loadAsResource(
                    main.getStoragePath(), main.getStorageFileName());
            if (resource == null || !resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.DOCUMENT_PDF_NOT_FOUND);
            }
            try (java.io.InputStream input = resource.getInputStream()) {
                byte[] bytes = input.readAllBytes();
                if (bytes.length == 0) {
                    throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT, "Tệp PDF chính rỗng");
                }
                return bytes;
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Không thể đọc PDF chính của văn bản {}", document.getId(), ex);
            throw new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        }
    }

    private boolean isPdfAttachment(DocumentAttachment att) {
        return "PDF".equalsIgnoreCase(att.getFileExtension());
    }
}

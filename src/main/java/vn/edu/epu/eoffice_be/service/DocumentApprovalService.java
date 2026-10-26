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

    /**
     * Lấy danh sách cán bộ có thẩm quyền duyệt văn bản (theo đơn vị hoặc toàn hệ thống)
     */
    @Transactional(readOnly = true)
    public List<EligibleApproverResponse> getEligibleApprovers(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndDeleteFlgFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

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

        // Kiểm tra quyền: người tạo hoặc quản trị viên
        boolean isCreator = document.getCreatorUser().getId().equals(userId);
        boolean isAdmin = sender.getRole() == Role.ADMIN || sender.getRole() == Role.SYSTEM_ADMIN;
        if (!isCreator && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền gửi duyệt văn bản này");
        }

        // Kiểm tra trạng thái: chỉ cho phép gửi khi là DRAFT hoặc REJECTED
        
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

        // 2. Kiểm tra tệp đính kèm và xử lý nâng phiên bản khi gửi duyệt lại (Resubmission)
        boolean isResubmission = document.getStatus() == DocumentStatus.NEEDS_REVISION 
                || document.getStatus() == DocumentStatus.REJECTED;

        boolean hasMainAttachment = documentAttachmentRepository
                .existsByDocumentIdAndIsMainDocumentTrueAndDeleteFlgFalse(documentId);
        boolean hasAnyAttachment = documentAttachmentRepository
                .existsByDocumentIdAndDeleteFlgFalse(documentId);
        boolean hasContent = document.getContent() != null && !document.getContent().isBlank();

        if (isResubmission) {
            if (!hasMainAttachment) {
                throw new BusinessException(ErrorCode.DOCUMENT_INCOMPLETE_DOSSIER,
                        "Tệp PDF cũ đã bị đóng băng do bị từ chối/yêu cầu sửa. Bắt buộc phải tải lên tệp PDF mới (v2) trước khi gửi duyệt lại!");
            }
            int currentVer = document.getCurrentVersion() > 0
                    ? document.getCurrentVersion()
                    : 1;
            document.setCurrentVersion(currentVer + 1);
        } else {
            if (!hasMainAttachment && !hasAnyAttachment && !hasContent) {
                throw new BusinessException(ErrorCode.DOCUMENT_INCOMPLETE_DOSSIER,
                        "Hồ sơ chưa đầy đủ: Bắt buộc phải đính kèm tệp văn bản (DOCX hoặc PDF) hoặc nội dung chi tiết trước khi gửi duyệt");
            }
        }

        // Kiểm tra người nhận duyệt
        UserLogin approver = userLoginRepository.findByIdAndDeleteFlgFalse(request.getApproverUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người duyệt được chỉ định"));

        if (!isUserEligibleApprover(approver, document)) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_ELIGIBLE,
                    "Người được chọn không có thẩm quyền duyệt văn bản này hoặc không thuộc đơn vị phù hợp");
        }

        Instant now = Instant.now();

        // Tự động ký nháy lần 1 nếu hồ sơ có tệp PDF (Người soạn thảo ký nháy tự động)
        String signatureHash = null;
        byte[] pdfBytes = loadDocumentPdfBytes(document);
        if (pdfBytes != null && pdfBytes.length > 0 && pdfSigner != null) {
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
                byte[] stampedPdfBytes = signResult.signedPdfBytes();
                String newPdfReference = documentPdfStorageService.storeSignedPdf(
                        stampedPdfBytes,
                        "initial_signed_" + document.getId() + ".pdf",
                        document.getId()
                );
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
        }

        // Lưu trạng thái nguồn trước khi đổi trạng thái (Issue 18)
        DocumentStatus prevStatus = document.getStatus();

        // Chuyển trạng thái văn bản sang WAITING_APPROVAL
        document.setStatus(DocumentStatus.IN_REVIEW);
        document.setSubmittedAt(now);
        Document savedDocument = documentRepository.save(document);

        // Tạo bản ghi yêu cầu duyệt
        DocumentApprovalRequest approvalRequest = DocumentApprovalRequest.builder()
                .document(savedDocument)
                .senderUser(sender)
                .approverUser(approver)
                .status(ApprovalRequestStatus.PENDING)
                .submitNote(request.getSubmitNote())
                .submittedAt(now)
                .build();
        documentApprovalRequestRepository.save(approvalRequest);

        // Ghi nhật ký tiến trình với fromStatus chính xác từ trạng thái nguồn (Issue 18)
        String logNote = "Gửi văn bản tới cán bộ duyệt: " + approver.getUsername() +
                (request.getSubmitNote() != null ? ". Ghi chú: " + request.getSubmitNote() : "") +
                (signatureHash != null ? " [Đã ký nháy tự động, SHA256: " + signatureHash + "]" : "");

        DocumentProcessLog processLog = DocumentProcessLog.builder()
                .document(savedDocument)
                .actorUser(sender)
                .action(DocumentProcessAction.SUBMIT_APPROVAL)
                .fromStatus(prevStatus != null ? prevStatus.name() : DocumentStatus.DRAFT.name())
                .toStatus(DocumentStatus.IN_REVIEW.name())
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
        eventPublisher.publishEvent(new DocumentSubmittedEvent(
                this, savedDocument.getId(), sender.getId(), approver.getId(),
                cleanTitle, cleanSubmitNote));

        log.info("Người dùng id={} đã gửi duyệt văn bản id={} tới người duyệt id={}",
                sender.getId(), savedDocument.getId(), approver.getId());

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

        // Kiểm tra quyền: người tạo hoặc quản trị viên
        boolean isCreator = document.getCreatorUser().getId().equals(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN;
        if (!isCreator && !isAdmin) {
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
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SYSTEM_ADMIN) {
            return true;
        }

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

    private byte[] loadDocumentPdfBytes(Document document) {
        if (document == null) {
            return null;
        }

        if (document.getPrimaryFilePath() != null && !document.getPrimaryFilePath().isBlank()) {
            // Never replace a missing signed version with an unsigned original attachment.
            return documentPdfStorageService.readPdf(document.getPrimaryFilePath());
        }

        if (documentAttachmentRepository != null && document.getId() != null) {
            List<DocumentAttachment> allAttachments = documentAttachmentRepository
                    .findByDocumentIdAndDeleteFlgFalse(document.getId());

            if (allAttachments != null && !allAttachments.isEmpty()) {
                Optional<DocumentAttachment> mainAttachmentOpt = allAttachments.stream()
                        .filter(att -> att != null && Boolean.TRUE.equals(att.isMainDocument()))
                        .findFirst();

                DocumentAttachment targetAttachment;
                if (mainAttachmentOpt.isPresent()) {
                    DocumentAttachment mainAtt = mainAttachmentOpt.get();
                    if (!isPdfAttachment(mainAtt)) {
                        return null;
                    }
                    targetAttachment = mainAtt;
                } else {
                    targetAttachment = allAttachments.stream()
                            .filter(this::isPdfAttachment)
                            .findFirst()
                            .orElse(null);
                }

                if (targetAttachment != null && unifiedStorageService != null) {
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
                        }
                    } catch (Exception e) {
                        log.warn("Không thể tải tệp PDF đính kèm từ unifiedStorageService: {}", e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private boolean isPdfAttachment(DocumentAttachment att) {
        if (att == null) return false;
        return "PDF".equalsIgnoreCase(att.getFileExtension()) ||
                (att.getOriginalFileName() != null && att.getOriginalFileName().toLowerCase().endsWith(".pdf"));
    }
}

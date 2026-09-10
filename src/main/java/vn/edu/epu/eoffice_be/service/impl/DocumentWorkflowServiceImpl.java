package vn.edu.epu.eoffice_be.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentWorkflowServiceImpl implements DocumentWorkflowService {

    private final DocumentRepository documentRepository;
    private final DocumentWorkflowLogRepository documentWorkflowLogRepository;
    private final UserLoginRepository userLoginRepository;
    private final UserInfoRepository userInfoRepository;
    private final OrgNodeRepository orgNodeRepository;
    private final WorkflowEngine workflowEngine;
    private final FileStorageService fileStorageService;
    private final PdfProcessingService pdfProcessingService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    @Transactional
    public DocumentDetailResponse approve(Long documentId, DocumentApproveRequest request, Long actorId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = findActorOrThrow(actorId);

        OrgNode assignedOrg = null;
        if (request.getAssignedOrgNodeId() != null) {
            assignedOrg = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getAssignedOrgNodeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORG_NODE_PARENT_NOT_FOUND));
        }

        // Quyết định hành động chuyển tiếp phù hợp với trạng thái hiện tại
        WorkflowAction action;
        if (document.getStatus() == DocumentStatus.SUBMITTED || document.getStatus() == DocumentStatus.IN_REVIEW) {
            action = WorkflowAction.REVIEW_APPROVE;
        } else if (document.getStatus() == DocumentStatus.LEADER_REVIEW) {
            action = WorkflowAction.LEADER_APPROVE;
        } else {
            action = WorkflowAction.REVIEW_APPROVE;
        }

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
    public DocumentDetailResponse requestRevision(Long documentId, DocumentRevisionRequest request, Long actorId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = findActorOrThrow(actorId);

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
    @Transactional
    public DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request, Long actorId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = findActorOrThrow(actorId);

        // 1. Chuẩn bị file PDF nguồn: nếu chưa có file đính kèm, sinh file PDF mẫu để hỗ trợ test luồng
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

        // 5. Điều hướng luồng duyệt và ghi log
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
    @Transactional
    public DocumentDetailResponse signAndPublish(Long documentId, DocumentPublishRequest request, Long actorId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = findActorOrThrow(actorId);

        // 1. Cập nhật thông tin ban hành chính thức
        Instant issueDate = request.getIssueDate() != null ? request.getIssueDate() : Instant.now();
        Instant effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : issueDate;

        document.setOfficialNumber(request.getOfficialNumber());
        document.setIssueDate(issueDate);
        document.setEffectiveDate(effectiveDate);

        // 2. Chuẩn bị file PDF để ký số
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

        // 5. Chuyển trạng thái sang PUBLISHED và ghi log
        workflowEngine.executeTransition(
                document,
                WorkflowAction.SIGN_AND_PUBLISH,
                actor,
                request.getComment(),
                null,
                null,
                officialHash
        );

        return mapToDetailResponse(document);
    }

    @Override
    @Transactional
    public DocumentDetailResponse reject(Long documentId, DocumentRejectRequest request, Long actorId) {
        Document document = findDocumentOrThrow(documentId);
        UserLogin actor = findActorOrThrow(actorId);

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
    public List<DocumentWorkflowLogResponse> getWorkflowHistory(Long documentId) {
        findDocumentOrThrow(documentId);
        List<DocumentWorkflowLog> logs =
                documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(documentId);

        return logs.stream().map(this::mapToLogResponse).toList();
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

    private void ensureDocumentPdfExists(Document document) {
        if (document.getPrimaryFilePath() == null || document.getPrimaryFilePath().isBlank()) {
            byte[] samplePdf = pdfProcessingService.createSamplePdf(
                    document.getTitle(),
                    "Van ban: " + document.getTitle() + "\nMa loai: " + document.getDocumentTypeCd()
            );
            String storedPath = fileStorageService.storeFile(
                    samplePdf,
                    "sample_doc_" + document.getId() + ".pdf",
                    "drafts"
            );
            document.setPrimaryFilePath(storedPath);
            documentRepository.save(document);
        }
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
                .primaryFilePath(doc.getPrimaryFilePath())
                .officialFilePath(doc.getOfficialFilePath())
                .recipientsText(doc.getRecipientsText())
                .build();
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
}

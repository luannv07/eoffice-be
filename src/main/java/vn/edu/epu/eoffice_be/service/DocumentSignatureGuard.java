package vn.edu.epu.eoffice_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.springframework.stereotype.Service;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.SignatureItemResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentProcessLogRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.security.pki.PdfSignatureVerifier;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Requires cryptographic proof on the working PDF, bound to an actual signing operation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSignatureGuard {
    private final PdfSignatureVerifier verifier;
    private final DocumentWorkflowLogRepository workflowLogs;
    private final DocumentProcessLogRepository processLogs;

    public void requireInitial(Document document, UserLogin actor, InitialSignStage stage, byte[] pdf) {
        List<SignatureItemResponse> signatures = validSignatures(document, pdf);
        String uid = actor.getId().toString();
        for (SignatureItemResponse item : signatures) {
            if (!uid.equals(item.getUserId())) continue;
            boolean staged = stage.fieldName(uid).equals(item.getFieldName());
            boolean legacy = ("sig_user_" + uid).equals(item.getFieldName());
            if ((staged || legacy) && hasInitialRecord(document, actor, stage,
                    revisionHash(pdf, item.getFieldName()))) return;
        }
        throw missing("Bạn phải ký nháy ở bước " + stage + " trên bản PDF hiện tại trước khi chuyển tiếp");
    }

    public void requireLeader(Document document, UserLogin actor, byte[] pdf) {
        String field = "sig_approve_" + actor.getId();
        boolean present = validSignatures(document, pdf).stream().anyMatch(item ->
                actor.getId().toString().equals(item.getUserId()) && field.equals(item.getFieldName()));
        if (present) {
            String hash = revisionHash(pdf, field);
            boolean recorded = logs(document).stream().anyMatch(log -> sameActor(log, actor)
                    && (log.getAction() == WorkflowAction.LEADER_SIGN || log.getAction() == WorkflowAction.LEADER_APPROVE)
                    && log.getFromStatus() == DocumentStatus.PENDING_APPROVAL
                    && hash.equals(log.getSignatureHash()));
            if (recorded) return;
        }
        throw missing("Hiệu trưởng phải ký hình ảnh trên bản PDF hiện tại trước khi ký nháy hoặc chuyển văn thư");
    }

    private List<SignatureItemResponse> validSignatures(Document document, byte[] pdf) {
        if (document.getPrimaryFilePath() == null || document.getPrimaryFilePath().isBlank()
                || pdf == null || pdf.length == 0) throw missing("Văn bản chưa có bản PDF đã ký");
        var response = verifier.verify(pdf);
        if (response == null || response.getSignatures() == null || response.getSignatures().isEmpty()
                || response.getSignatures().stream().anyMatch(item -> !item.isFullyValid())) {
            throw missing("Chữ ký trên bản PDF hiện tại không hợp lệ; không thể chuyển tiếp");
        }
        return response.getSignatures();
    }

    private boolean hasInitialRecord(Document document, UserLogin actor, InitialSignStage stage, String hash) {
        if (logs(document).stream().anyMatch(log -> sameActor(log, actor)
                && log.getAction() == WorkflowAction.INITIAL_SIGN
                && log.getFromStatus() != null && matchesStage(log.getFromStatus(), stage)
                && hash.equals(log.getSignatureHash()))) return true;
        // Before separation, the drafter's signature was recorded in the submission process log.
        return stage == InitialSignStage.DRAFTER && processLogs
                .findByDocumentIdAndDeleteFlgFalseOrderByLogTimeDesc(document.getId()).stream()
                .anyMatch(log -> log.getActorUser() != null && actor.getId().equals(log.getActorUser().getId())
                        && log.getAction() == DocumentProcessAction.SUBMIT_APPROVAL && log.getNote() != null
                        && log.getNote().contains("[Đã ký nháy tự động, SHA256: " + hash + "]"));
    }

    private boolean matchesStage(DocumentStatus status, InitialSignStage stage) {
        return switch (stage) {
            case DRAFTER -> status == DocumentStatus.DRAFT || status == DocumentStatus.NEEDS_REVISION
                    || status == DocumentStatus.REJECTED;
            case REVIEWER -> status == DocumentStatus.IN_REVIEW;
            case LEADER -> status == DocumentStatus.PENDING_APPROVAL;
            case CLERK -> status == DocumentStatus.APPROVED;
        };
    }

    private List<DocumentWorkflowLog> logs(Document document) {
        return workflowLogs.findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(document.getId());
    }

    private boolean sameActor(DocumentWorkflowLog log, UserLogin actor) {
        return log.getActor() != null && Objects.equals(actor.getId(), log.getActor().getId());
    }

    private String revisionHash(byte[] pdf, String fieldName) {
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            for (PDSignatureField field : parsed.getSignatureFields()) {
                if (!fieldName.equals(field.getFullyQualifiedName()) || field.getSignature() == null) continue;
                int[] range = field.getSignature().getByteRange();
                if (range == null || range.length != 4) break;
                long end = (long) range[2] + range[3];
                if (end <= 0 || end > pdf.length) break;
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Arrays.copyOf(pdf, (int) end)));
            }
        } catch (Exception e) {
            log.warn("Không thể đối chiếu phiên bản PDF đã ký, signatureField={}", fieldName, e);
            throw missing("Không thể đối chiếu phiên bản PDF đã ký");
        }
        throw missing("Không tìm thấy chữ ký yêu cầu trên PDF hiện tại");
    }

    private BusinessException missing(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}

package vn.edu.epu.eoffice_be.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentLeaderSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentSignatureVerifyResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;

import java.util.List;

/**
 * Interface định nghĩa các nghiệp vụ xử lý luồng văn bản (Workflow).
 * <p>
 * Triển khai mặc định bởi Spring-managed bean {@link vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl}.
 * </p>
 *
 * @see vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl
 */
public interface DocumentWorkflowService {

    /**
     * UC12: Chuyển tiếp văn bản đã ký, ghi nhận bút phê và đơn vị chủ trì; không ký PDF.
     */
    DocumentDetailResponse approve(Long documentId, DocumentApproveRequest request);

    /**
     * UC12: Yêu cầu chỉnh sửa văn bản, chuyển trạng thái về NEEDS_REVISION.
     */
    DocumentDetailResponse requestRevision(Long documentId, DocumentRevisionRequest request);

    /**
     * UC13: Ký nháy số theo giai đoạn, lưu hash và lịch sử, giữ nguyên trạng thái.
     */
    DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request);

    /** Ký hình ảnh của lãnh đạo, không chuyển trạng thái. */
    DocumentDetailResponse leaderSign(Long documentId, DocumentLeaderSignRequest request);

    /**
     * UC14: Văn thư ký niêm phong, phát hành và phân phối bằng chữ ký số PKI X.509 PAdES,
     * đặt cờ CERTIFIED_NO_CHANGES_ALLOWED cho văn bản đã được văn thư cấp số và ký nháy.
     */
    DocumentDetailResponse signAndPublish(Long documentId, DocumentPublishRequest request);

    /**
     * UC14: Từ chối phê duyệt văn bản kèm lý do.
     */
    DocumentDetailResponse reject(Long documentId, DocumentRejectRequest request);

    /**
     * Lấy timeline lịch sử luồng duyệt của một văn bản (hỗ trợ phân trang).
     */
    Page<DocumentWorkflowLogResponse> getWorkflowHistory(Long documentId, Pageable pageable);

    /**
     * Lấy thông tin chi tiết một văn bản.
     */
    DocumentDetailResponse getDetail(Long documentId);

    /**
     * UC17.1: Xem timeline luồng điều hành & bút phê lãnh đạo (tính năng của đồng đội).
     */
    List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId);
    List<WorkflowTimelineResponse> getDocumentTimeline(Long documentId, org.springframework.security.core.Authentication authentication);

    void verifyUserCanAccessDocument(Long documentId, org.springframework.security.core.Authentication authentication);

    /**
     * UC17.2: Danh sách văn bản tồn đọng / chờ xử lý (tính năng của đồng đội).
     */
    Page<PendingDocumentResponse> getPendingDocuments(Pageable pageable);
    List<PendingDocumentResponse> getPendingDocuments();

    /**
     * UC-NEW-1: Danh sách văn bản chờ ký số (PENDING_SIGN).
     */
    Page<PendingDocumentResponse> getPendingSignDocuments(Pageable pageable);

    /**
     * UC-NEW-2: Danh sách văn bản chờ ban hành (PENDING_ISSUANCE).
     */
    Page<PendingDocumentResponse> getPendingPublishDocuments(Pageable pageable);

    /**
     * UC-SIG-VERIFY: Kiểm định 7 tiêu chí bảo mật toàn bộ chữ ký số trên tài liệu PDF.
     */
    DocumentSignatureVerifyResponse verifyDocumentSignatures(Long documentId);

    /**
     * UC-REVOKE: Thu hồi văn bản đã ban hành chính thức, chuyển trạng thái sang REVOKED
     * và tự động đồng bộ số serial của chứng thư vào danh sách thu hồi CRL.
     */
    DocumentDetailResponse revokeDocument(Long documentId, vn.edu.epu.eoffice_be.dto.request.DocumentRevokeRequest request);

    default void logAction(Long documentId, String username, String role, String action, String comment) {}
}
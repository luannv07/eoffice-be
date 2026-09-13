package vn.edu.epu.eoffice_be.service;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.edu.epu.eoffice_be.dto.PendingDocumentResponse;
import vn.edu.epu.eoffice_be.dto.WorkflowTimelineResponse;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;

import java.util.List;

public interface DocumentWorkflowService {

    /**
     * UC12: Phê duyệt văn bản, ghi nhận bút phê lãnh đạo và đơn vị chủ trì.
     */
    DocumentDetailResponse approve(Long documentId, DocumentApproveRequest request);

    /**
     * UC12: Yêu cầu chỉnh sửa văn bản, chuyển trạng thái về NEEDS_REVISION.
     */
    DocumentDetailResponse requestRevision(Long documentId, DocumentRevisionRequest request);

    /**
     * UC13: Ký nháy văn bản, nhúng Digital Stamp lên trang cuối PDF và tính SHA-256 hash.
     */
    DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request);

    /**
     * UC14: Phê duyệt và ký phát hành chính thức bằng chữ ký số PKI X.509 PAdES,
     * đặt cờ CERTIFIED_NO_CHANGES_ALLOWED và cấp số hiệu văn bản.
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

    /**
     * UC17.2: Danh sách văn bản tồn đọng / chờ xử lý (tính năng của đồng đội).
     */
    Page<PendingDocumentResponse> getPendingDocuments(Pageable pageable);
    List<PendingDocumentResponse> getPendingDocuments();

    default void logAction(Long documentId, String username, String role, String action, String comment) {}
}
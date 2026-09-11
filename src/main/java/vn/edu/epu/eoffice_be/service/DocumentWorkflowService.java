package vn.edu.epu.eoffice_be.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    DocumentDetailResponse approve(Long documentId, DocumentApproveRequest request, Long actorId);

    /**
     * UC12: Yêu cầu chỉnh sửa văn bản, chuyển trạng thái về NEEDS_REVISION.
     */
    DocumentDetailResponse requestRevision(Long documentId, DocumentRevisionRequest request, Long actorId);

    /**
     * UC13: Ký nháy văn bản, nhúng Digital Stamp lên trang cuối PDF và tính SHA-256 hash.
     */
    DocumentDetailResponse initialSign(Long documentId, DocumentInitialSignRequest request, Long actorId);

    /**
     * UC14: Phê duyệt và ký phát hành chính thức bằng chữ ký số PKI X.509 PAdES,
     * đặt cờ CERTIFIED_NO_CHANGES_ALLOWED và cấp số hiệu văn bản.
     */
    DocumentDetailResponse signAndPublish(Long documentId, DocumentPublishRequest request, Long actorId);

    /**
     * UC14: Từ chối phê duyệt văn bản kèm lý do.
     */
    DocumentDetailResponse reject(Long documentId, DocumentRejectRequest request, Long actorId);

    /**
     * Lấy timeline lịch sử luồng duyệt của một văn bản (hỗ trợ phân trang).
     *
     * @param documentId ID của văn bản
     * @param pageable   Thông tin phân trang và sắp xếp
     * @return Trang danh sách các log lịch sử luồng duyệt
     */
    Page<DocumentWorkflowLogResponse> getWorkflowHistory(Long documentId, Pageable pageable);

    /**
     * Lấy thông tin chi tiết một văn bản.
     */
    DocumentDetailResponse getDetail(Long documentId);
}

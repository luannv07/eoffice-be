package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Kết quả kiểm định toàn bộ chữ ký số trên tài liệu PDF")
public class DocumentSignatureVerifyResponse {

    @Schema(description = "ID văn bản")
    private Long documentId;

    @Schema(description = "Trích yếu văn bản")
    private String documentTitle;

    @Schema(description = "Đánh giá tổng thể tài liệu: VALID (Hợp lệ 100%), WARNING (Có cảnh báo), INVALID (Không hợp lệ)")
    private String overallStatus;

    @Schema(description = "Mô tả trạng thái tổng thể bằng tiếng Việt")
    private String overallStatusDesc;

    @Schema(description = "Tổng số lượng chữ ký số tìm thấy trên tài liệu")
    private int totalSignatures;

    @Schema(description = "Danh sách chi tiết kết quả kiểm định từng chữ ký")
    private List<SignatureItemResponse> signatures;

    @Schema(description = "Danh sách cảnh báo hoặc lỗi phát hiện được")
    private List<String> warningsOrErrors;

    @Schema(description = "Thông điệp tổng kết quá trình kiểm định")
    private String summaryMessage;
}

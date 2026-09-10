package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu phê duyệt văn bản (UC12)")
public class DocumentApproveRequest {

    @Schema(description = "Ý kiến nhận xét hoặc ghi chú", example = "Đồng ý thông qua kế hoạch")
    private String comment;

    @Schema(description = "Ý kiến chỉ đạo / Bút phê lãnh đạo (Leadership Endorsement)", example = "Giao Phòng Đào tạo chủ trì thực hiện trước ngày 30/10")
    private String leadershipInstruction;

    @Schema(description = "ID phòng ban / đơn vị được chỉ định chủ trì thực hiện", example = "1002")
    private Long assignedOrgNodeId;
}

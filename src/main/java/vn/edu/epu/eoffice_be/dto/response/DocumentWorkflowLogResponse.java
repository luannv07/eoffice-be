package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin nhật ký một bước trong luồng phê duyệt văn bản")
public class DocumentWorkflowLogResponse {

    @Schema(description = "ID bản ghi nhật ký")
    private Long id;

    @Schema(description = "ID văn bản")
    private Long documentId;

    @Schema(description = "Mã hành động thực hiện")
    private WorkflowAction action;

    @Schema(description = "Tên hiển thị của hành động")
    private String actionName;

    @Schema(description = "ID người thực hiện")
    private Long actorId;

    @Schema(description = "Tên tài khoản người thực hiện")
    private String actorUsername;

    @Schema(description = "Trạng thái trước khi chuyển")
    private DocumentStatus fromStatus;

    @Schema(description = "Trạng thái sau khi chuyển")
    private DocumentStatus toStatus;

    @Schema(description = "Ý kiến nhận xét / Lý do trả về hoặc từ chối")
    private String comment;

    @Schema(description = "Bút phê lãnh đạo (Leadership Instruction)")
    private String leadershipInstruction;

    @Schema(description = "ID đơn vị chủ trì được chỉ định")
    private Long assignedOrgNodeId;

    @Schema(description = "Tên đơn vị chủ trì được chỉ định")
    private String assignedOrgNodeName;

    @Schema(description = "Mã băm SHA-256 chữ ký số hoặc ký nháy")
    private String signatureHash;

    @Schema(description = "Thời điểm thực hiện")
    private Instant createdDate;
}

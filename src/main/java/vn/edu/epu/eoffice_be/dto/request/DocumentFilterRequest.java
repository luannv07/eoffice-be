package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentFilterRequest {

    @Schema(description = "Từ khóa tìm kiếm theo tiêu đề, mã dự thảo hoặc trích yếu", example = "quy chế")
    String keyword;

    @Schema(description = "Lọc theo trạng thái văn bản", example = "DRAFT")
    DocumentStatus status;

    @Schema(description = "Lọc theo đơn vị ban hành", example = "1001")
    Long orgNodeId;

    @Schema(description = "Số trang (bắt đầu từ 0)", example = "0")
    @Builder.Default
    int page = 0;

    @Schema(description = "Số bản ghi trên mỗi trang", example = "20")
    @Builder.Default
    int size = 20;
}

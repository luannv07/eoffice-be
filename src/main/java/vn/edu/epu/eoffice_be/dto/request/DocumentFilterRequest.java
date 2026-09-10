package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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

    @Size(max = 255, message = "Từ khóa tìm kiếm không được vượt quá 255 ký tự")
    @Schema(description = "Từ khóa tìm kiếm theo tiêu đề, mã dự thảo hoặc trích yếu", example = "quy chế")
    String keyword;

    @Schema(description = "Lọc theo trạng thái văn bản", example = "DRAFT")
    DocumentStatus status;

    @Schema(description = "Lọc theo đơn vị ban hành", example = "1001")
    Long orgNodeId;

    @PositiveOrZero(message = "Số trang phải lớn hơn hoặc bằng 0")
    @Schema(description = "Số trang (bắt đầu từ 0)", example = "0")
    @Builder.Default
    int page = 0;

    @Min(value = 1, message = "Số bản ghi trên mỗi trang phải tối thiểu là 1")
    @Max(value = 100, message = "Số bản ghi trên mỗi trang không được vượt quá 100")
    @Schema(description = "Số bản ghi trên mỗi trang", example = "20")
    @Builder.Default
    int size = 20;
}

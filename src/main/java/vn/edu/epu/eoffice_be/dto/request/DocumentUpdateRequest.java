package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentUpdateRequest {

    @NotBlank(message = "Tiêu đề văn bản không được để trống")
    @Size(max = 500, message = "Tiêu đề không được vượt quá 500 ký tự")
    @Schema(description = "Tiêu đề dự thảo văn bản", example = "Quyết định ban hành quy chế đào tạo đại học năm 2026 (Cập nhật)")
    String title;

    @NotBlank(message = "Trích yếu không được để trống")
    @Schema(description = "Trích yếu tóm tắt nội dung văn bản", example = "Quy định về đào tạo hệ chính quy và xét tốt nghiệp")
    String summary;

    @Schema(description = "Nội dung tóm tắt hoặc nội dung dự thảo", example = "Căn cứ Luật Giáo dục Đại học sửa đổi...")
    String content;

    @NotBlank(message = "Loại văn bản không được để trống")
    @Schema(description = "Mã loại văn bản trong từ điển DOCUMENT_TYPE_CD", example = "QUYET_DINH")
    String documentTypeCd;

    @NotNull(message = "Đơn vị ban hành không được để trống")
    @Schema(description = "ID đơn vị ban hành / chủ trì soạn thảo", example = "1001")
    Long issuingOrgNodeId;

    @Schema(description = "Độ mật (NORMAL, CONFIDENTIAL, SECRET)", example = "NORMAL")
    @Builder.Default
    DocumentSecurityLevel securityLevel = DocumentSecurityLevel.NORMAL;

    @Schema(description = "Độ khẩn (NORMAL, URGENT, TOP_URGENT)", example = "NORMAL")
    @Builder.Default
    DocumentUrgencyLevel urgencyLevel = DocumentUrgencyLevel.NORMAL;

    @Schema(description = "Tóm tắt lý do hoặc nội dung thay đổi của phiên bản mới", example = "Chỉnh sửa điều 5 theo yêu cầu của phòng Đào tạo")
    String changeSummary;

    @Schema(description = "Có lưu thành một phiên bản mới hay không (mặc định: true)", example = "true")
    @Builder.Default
    boolean saveNewVersion = true;
}

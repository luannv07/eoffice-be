package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin chi tiết hồ sơ văn bản")
public class DocumentDetailResponse {

    @Schema(description = "ID văn bản")
    private Long id;

    @Schema(description = "Trích yếu văn bản")
    private String title;

    @Schema(description = "Mã loại văn bản")
    private String documentTypeCd;

    @Schema(description = "ID đơn vị ban hành")
    private Long issuingOrgId;

    @Schema(description = "Tên đơn vị ban hành")
    private String issuingOrgName;

    @Schema(description = "ID người soạn thảo")
    private Long drafterId;

    @Schema(description = "Tên người soạn thảo")
    private String drafterUsername;

    @Schema(description = "ID người ký phát hành")
    private Long signerId;

    @Schema(description = "Tên người ký phát hành")
    private String signerUsername;

    @Schema(description = "Độ khẩn")
    private String urgencyLevelCd;

    @Schema(description = "Độ mật")
    private String securityLevelCd;

    @Schema(description = "Trạng thái luồng duyệt hiện tại")
    private DocumentStatus status;

    @Schema(description = "Số hiệu văn bản chính thức")
    private String officialNumber;

    @Schema(description = "Ngày ký ban hành")
    private Instant issueDate;

    @Schema(description = "Ngày có hiệu lực")
    private Instant effectiveDate;

    @Schema(description = "Mã băm SHA-256 niêm phong file chính thức")
    private String officialHash;

    @Schema(description = "URL tải file PDF dự thảo hiện tại", example = "/documents/100/files/primary")
    private String primaryFileUrl;

    @Schema(description = "URL tải file PDF chính thức sau khi ký phát hành", example = "/documents/100/files/official")
    private String officialFileUrl;

    @Schema(description = "Nơi nhận và lưu")
    private String recipientsText;

    @Schema(description = "Thông tin cơ bản văn bản")
    private DocumentResponse document;

    @Schema(description = "Danh sách tệp đính kèm")
    private List<DocumentAttachmentResponse> attachments;

    @Schema(description = "Danh sách phiên bản văn bản")
    private List<DocumentVersionResponse> versions;
}
package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Kết quả kiểm định chi tiết của một chữ ký số trên tài liệu PDF theo 7 tiêu chí an toàn")
public class SignatureItemResponse {

    @Schema(description = "Thứ tự chữ ký trong tài liệu (1, 2, ...)")
    private int index;

    @Schema(description = "Tên trường chữ ký số (Signature Field Name, vd: sig_user_1001)")
    private String fieldName;

    @Schema(description = "ID người ký trích xuất từ chứng thư số")
    private String userId;

    @Schema(description = "Họ và tên người ký trích xuất từ Subject DN (CN)")
    private String signerName;

    @Schema(description = "Chức vụ của người ký trích xuất từ Subject DN (Title / T)")
    private String signerTitle;

    @Schema(description = "Đơn vị/Phòng ban của người ký trích xuất từ Subject DN (OU)")
    private String organizationUnit;

    @Schema(description = "Số Serial của chứng thư số (Hexadecimal)")
    private String serialHex;

    @Schema(description = "Thời gian ký số")
    private LocalDateTime signDate;

    // 1. INTEGRITY
    @Schema(description = "Tiêu chí 1: Tính toàn vẹn băm SHA-256 và chữ ký mật mã CMS")
    private boolean integrityPass;

    @Schema(description = "Chi tiết kiểm tra tính toàn vẹn")
    private String integrityDetails;

    // 2. CHAIN
    @Schema(description = "Tiêu chí 2: Chuỗi tin cậy với Root CA nội bộ")
    private boolean chainPass;

    @Schema(description = "Chi tiết kiểm tra chuỗi chứng thư")
    private String chainDetails;

    // 3. VALIDITY
    @Schema(description = "Tiêu chí 3: Thời hạn hiệu lực chứng thư tại thời điểm ký và hiện tại")
    private boolean validityPass;

    @Schema(description = "Chi tiết kiểm tra thời hạn hiệu lực")
    private String validityDetails;

    // 4. KEY USAGE
    @Schema(description = "Tiêu chí 4: Mục đích sử dụng khóa (digitalSignature, nonRepudiation) và CA=false")
    private boolean keyUsagePass;

    @Schema(description = "Chi tiết kiểm tra KeyUsage")
    private String keyUsageDetails;

    // 5. REVOCATION
    @Schema(description = "Tiêu chí 5: Trạng thái thu hồi chứng thư (CRL/Revoked)")
    private boolean revocationPass;

    @Schema(description = "Chi tiết kiểm tra trạng thái thu hồi")
    private String revocationDetails;

    // 6. COVERAGE
    @Schema(description = "Tiêu chí 6: Vùng bao phủ ByteRange và kiểm tra Incremental Update")
    private boolean coveragePass;

    @Schema(description = "Chi tiết kiểm tra vùng bao phủ ByteRange")
    private String coverageDetails;

    // 7. IDENTITY
    @Schema(description = "Tiêu chí 7: Đối chiếu định danh User ID trong chứng thư với trường chữ ký")
    private boolean identityPass;

    @Schema(description = "Chi tiết đối chiếu định danh")
    private String identityDetails;

    @Schema(description = "Đạt toàn bộ 7 tiêu chí bảo mật")
    private boolean fullyValid;
}

package vn.edu.epu.eoffice_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Chi tiết user cho admin, bao gồm hồ sơ (UserInfo) nếu đã có")
public class UserDetailResponse {

	@Schema(description = "ID user")
	Long id;

	@Schema(description = "Tên đăng nhập")
	String username;

	@Schema(description = "Email liên hệ")
	String email;

	@Schema(description = "Vai trò")
	Role role;

	@Schema(description = "Trạng thái: ACTIVE / LOCKED / DISABLED")
	String userStatusCd;

	@Schema(description = "Bắt buộc đổi mật khẩu lần sau")
	boolean mustChangePassword;

	@Schema(description = "Thời điểm mật khẩu hết hạn")
	Instant passwordExpiredAt;

	@Schema(description = "Thời điểm hết hạn khóa đăng nhập (nếu có)")
	Instant lockoutUntil;

	@Schema(description = "Số lần đăng nhập sai liên tiếp")
	int failedLoginAttempts;

	@Schema(description = "Thời điểm đăng nhập gần nhất")
	Instant lastLoginAt;

	@Schema(description = "Đã bị xóa mềm hay chưa")
	boolean deleted;

	@Schema(description = "Đã hoàn thiện hồ sơ (có bản ghi UserInfo) hay chưa")
	boolean profileCompleted;

	@Schema(description = "Họ tên từ hồ sơ (nếu có)")
	String fullName;

	@Schema(description = "Mã nhân viên từ hồ sơ (nếu có)")
	String employeeCode;

	@Schema(description = "Ngày sinh từ hồ sơ (nếu có)")
	LocalDate dateOfBirth;

	@Schema(description = "Giới tính từ hồ sơ (nếu có)")
	String gender;

	@Schema(description = "Email liên hệ phụ từ hồ sơ (nếu có)")
	String contactEmail;

	@Schema(description = "Số điện thoại liên hệ từ hồ sơ (nếu có)")
	String contactPhone;

	@Schema(description = "Địa chỉ từ hồ sơ (nếu có)")
	String address;

	@Schema(description = "Học hàm từ hồ sơ (nếu có)")
	String academicTitle;

	@Schema(description = "Học vị từ hồ sơ (nếu có)")
	String academicDegree;

	@Schema(description = "Ngày vào tổ chức từ hồ sơ (nếu có)")
	LocalDate joinOrgDate;

	@Schema(description = "Trạng thái nhân sự từ hồ sơ (nếu có)")
	String employmentStatusCd;

	@Schema(description = "Thời điểm tạo")
	Instant createdDate;

	@Schema(description = "Thời điểm cập nhật gần nhất")
	Instant updatedDate;
}

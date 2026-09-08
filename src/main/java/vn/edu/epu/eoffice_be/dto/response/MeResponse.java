package vn.edu.epu.eoffice_be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.enums.Role;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Thông tin tài khoản + hồ sơ của người dùng đang đăng nhập")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MeResponse {

	@Schema(description = "Id người dùng", example = "1002")
	Long id;
	@Schema(description = "Tên đăng nhập", example = "nguyen.van.a")
	String username;
	@Schema(description = "Email liên hệ", example = "nguyen.van.a@epu.edu.vn")
	String email;
	@Schema(description = "Role hệ thống", example = "USER")
	Role role;
	@Schema(description = "Mã trạng thái tài khoản (dictionary USER_STATUS_CD: ACTIVE / LOCKED / DISABLED)", example = "ACTIVE")
	String userStatusCd;
	@Schema(description = "true nếu bắt buộc đổi mật khẩu trước khi dùng hệ thống", example = "false")
	boolean mustChangePassword;
	@Schema(description = "Thời điểm mật khẩu hết hạn (null = không giới hạn)", example = "2026-11-01T00:00:00Z", nullable = true)
	Instant passwordExpiredAt;
	@Schema(description = "Lần đăng nhập gần nhất", example = "2026-08-22T01:15:30Z")
	Instant lastLoginAt;

	@Schema(description = "Họ và tên đầy đủ", example = "Nguyễn Văn A")
	String fullName;
	@Schema(description = "Ngày sinh (định dạng yyyy-MM-dd)", example = "1990-05-12")
	LocalDate dateOfBirth;
	@Schema(description = "Giới tính (dictionary GENDER_CD nếu có)", example = "Nam")
	String gender;
	@Schema(description = "Số CCCD", example = "038090001234")
	String nationalIdNumber;
	@Schema(description = "Đường dẫn ảnh đại diện", example = "/uploads/avatar/nguyen-van-a.png")
	String avatarUrl;
	@Schema(description = "Email liên hệ (từ hồ sơ user_infos)", example = "nguyen.van.a@epu.edu.vn")
	String contactEmail;
	@Schema(description = "Số điện thoại liên hệ", example = "0912345678")
	String contactPhone;
	@Schema(description = "Địa chỉ liên hệ", example = "Số 10, đường Nguyễn Trãi, Quận Thanh Xuân, Hà Nội")
	String address;
	@Schema(description = "Mã nhân viên", example = "GV2018045")
	String employeeCode;
	@Schema(description = "Chức danh học thuật (Phó giáo sư, Giáo sư...)", example = "Trợ giảng")
	String academicTitle;
	@Schema(description = "Học vị (Thạc sĩ, Tiến sĩ...)", example = "Thạc sĩ")
	String academicDegree;
	@Schema(description = "Ngày vào Đảng (nếu có, định dạng yyyy-MM-dd)", example = "2015-06-09", nullable = true)
	LocalDate partyMembershipDate;
	@Schema(description = "Mã trạng thái nhân sự (dictionary EMPLOYMENT_STATUS_CD: ACTIVE / RETIRED / RESIGNED / ON_LEAVE)", example = "ACTIVE")
	String employmentStatusCd;
	@Schema(description = "Ngày vào đơn vị (định dạng yyyy-MM-dd)", example = "2018-09-01")
	LocalDate joinOrgDate;

	@Schema(description = "true khi đã có bản ghi hồ sơ user_infos — false thì FE hiện popup điền hồ sơ", example = "true")
	boolean profileCompleted;
}

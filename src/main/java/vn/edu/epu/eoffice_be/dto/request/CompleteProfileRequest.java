package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request user tự hoàn thiện hồ sơ lần đầu (upsert user_infos của chính mình)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteProfileRequest {

	@Schema(description = "Họ và tên đầy đủ", example = "Nguyễn Văn A", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "fullName không được để trống")
	@Size(max = 255, message = "fullName tối đa 255 ký tự")
	String fullName;

	@Schema(description = "Ngày sinh (yyyy-MM-dd)", example = "1990-05-12")
	@Past(message = "dateOfBirth phải là ngày trong quá khứ")
	LocalDate dateOfBirth;

	@Schema(description = "Giới tính", example = "Nam")
	@Size(max = 10, message = "gender tối đa 10 ký tự")
	String gender;

	@Schema(description = "Số CCCD/CMND", example = "038090001234")
	@Size(max = 20, message = "nationalIdNumber tối đa 20 ký tự")
	String nationalIdNumber;

	@Schema(description = "Đường dẫn ảnh đại diện", example = "/uploads/avatar/nguyen-van-a.png")
	@Size(max = 500, message = "avatarUrl tối đa 500 ký tự")
	String avatarUrl;

	@Schema(description = "Email liên hệ (duy nhất, khác email đăng nhập)", example = "nguyen.van.a@epu.edu.vn")
	@Email(message = "contactEmail không hợp lệ")
	@Size(max = 255, message = "contactEmail tối đa 255 ký tự")
	String contactEmail;

	@Schema(description = "Số điện thoại liên hệ", example = "0912345678")
	@Size(max = 20, message = "contactPhone tối đa 20 ký tự")
	String contactPhone;

	@Schema(description = "Địa chỉ liên hệ", example = "Số 10, đường Nguyễn Trãi, Hà Nội")
	@Size(max = 500, message = "address tối đa 500 ký tự")
	String address;

	@Schema(description = "Mã nhân viên (duy nhất)", example = "GV2018045")
	@Size(max = 50, message = "employeeCode tối đa 50 ký tự")
	String employeeCode;

	@Schema(description = "Chức danh học thuật", example = "Trợ giảng")
	@Size(max = 50, message = "academicTitle tối đa 50 ký tự")
	String academicTitle;

	@Schema(description = "Học vị", example = "Thạc sĩ")
	@Size(max = 50, message = "academicDegree tối đa 50 ký tự")
	String academicDegree;

	@Schema(description = "Ngày vào Đảng (nếu có, yyyy-MM-dd)", example = "2015-06-09")
	@PastOrPresent(message = "partyMembershipDate không được là ngày tương lai")
	LocalDate partyMembershipDate;

	@Schema(description = "Mã trạng thái nhân sự (mặc định ACTIVE)", example = "ACTIVE")
	@Size(max = 20, message = "employmentStatusCd tối đa 20 ký tự")
	String employmentStatusCd;

	@Schema(description = "Ngày vào đơn vị (yyyy-MM-dd)", example = "2018-09-01")
	LocalDate joinOrgDate;
}

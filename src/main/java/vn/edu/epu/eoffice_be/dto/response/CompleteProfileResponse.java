package vn.edu.epu.eoffice_be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kết quả hoàn thiện hồ sơ: hồ sơ mới + accessToken mới (profileCompleted=true). FE thay token ngay, không cần đăng nhập lại.")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteProfileResponse {

	@Schema(description = "Access token JWT mới với claim profileCompleted=true", example = "eyJhbGciOiJIUzI1NiJ9...")
	String accessToken;

	@Schema(description = "Thời gian sống của access token (giây)", example = "900")
	Long expiresIn;

	@Schema(description = "Hồ sơ sau khi hoàn thiện (giống GET /auth/me, profileCompleted=true)")
	MeResponse profile;
}

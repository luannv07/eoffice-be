package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/public")
@Auditable
@Tag(name = "Public", description = "Endpoint công khai, không cần xác thực — dùng kiểm tra hệ thống và chạy pipeline")
public class PublicController {
	@Operation(
					summary = "Kiểm tra hệ thống",
					description = "Endpoint test public trả về thời điểm xử lý — dùng để verify pipeline CI/CD và health check thủ công."
	)
	@ApiResponses({
					@ApiResponse(responseCode = "200", description = "Hệ thống hoạt động")
	})
	@SecurityRequirements
	@GetMapping("/test-base")
	public BaseResponse<String> health() {
		log.info("Test chạy pipeline: {}", Instant.now());
		return BaseResponse.success("OK: " + Instant.now());
	}
}

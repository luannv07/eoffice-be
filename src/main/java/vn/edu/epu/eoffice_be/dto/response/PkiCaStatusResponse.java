package vn.edu.epu.eoffice_be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Trạng thái và thông tin công khai của Root CA; không chứa khóa riêng hoặc mật khẩu")
public record PkiCaStatusResponse(
        State state,
        boolean canInitialize,
        String subject,
        String fingerprintSha256,
        Instant notBefore,
        Instant notAfter) {
    public enum State { NOT_INITIALIZED, READY, EXPIRED, RECOVERY_REQUIRED }
}

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
        Instant notAfter,
        Diagnostics diagnostics) {
    public PkiCaStatusResponse(State state, boolean canInitialize, String subject,
                               String fingerprintSha256, Instant notBefore, Instant notAfter) {
        this(state, canInitialize, subject, fingerprintSha256, notBefore, notAfter, null);
    }

    public enum State { NOT_INITIALIZED, READY, EXPIRED, RECOVERY_REQUIRED }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Chẩn đoán PKI trong database; không chứa dữ liệu khóa, đường dẫn hoặc mật khẩu. "
            + "hasSigningHistory chỉ có khi đã kiểm tra lịch sử trong database.")
    public record Diagnostics(Reason reason, boolean caRecordExists, Boolean hasSigningHistory) { }

    public enum Reason {
        CA_RECORD_MISSING_WITH_SIGNING_HISTORY,
        CA_RECORD_MISSING_WITH_USER_KEYSTORES,
        LEGACY_PKI_REQUIRES_MIGRATION,
        NO_ACTIVE_CA,
        CA_KEYSTORE_UNREADABLE,
        CA_CERTIFICATE_UNREADABLE,
        CA_CERTIFICATE_MISMATCH,
        CA_FINGERPRINT_MISMATCH,
        CA_CERTIFICATE_INVALID,
        CA_KEY_CERTIFICATE_MISMATCH,
        CA_NOT_YET_VALID,
        CA_VALIDATION_FAILED
    }
}

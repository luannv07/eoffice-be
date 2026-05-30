package vn.edu.epu.eoffice_be.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T extends @Nullable Object> {

    @Schema(description = "Trạng thái thành công hay thất bại", example = "true")
    boolean success;

    @Schema(description = "Dữ liệu trả về (nếu có)")
    @Nullable
    T data;

    @Schema(description = "Thông báo", example = "Thành công")
    String message;

    @Schema(description = "Mã lỗi (nếu có)", example = "UNAUTHORIZED")
    String errorCode;

    @Schema(description = "Thời gian phản hồi", example = "2025-12-11T09:09:16Z")
    Instant timestamp;

    public static <T extends @Nullable Object> BaseResponse<T> success(T data) {
        return BaseResponse.<T>builder()
                .success(true)
                .data(data)
                .message("Thành công")
                .timestamp(Instant.now())
                .build();
    }

    public static <T extends @Nullable Object> BaseResponse<T> success(T data, String message) {
        return BaseResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T extends @Nullable Object> BaseResponse<T> error(String message) {
        return BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T extends @Nullable Object> BaseResponse<T> error(String message, String errorCode) {
        return BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(Instant.now())
                .build();
    }
}

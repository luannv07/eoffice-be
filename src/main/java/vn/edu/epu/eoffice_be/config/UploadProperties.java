package vn.edu.epu.eoffice_be.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "eoffice.upload")
@Validated
@Getter
@Setter
public class UploadProperties {
    /**
     * Đường dẫn tuyệt đối thư mục lưu trữ tài liệu, đảm bảo không ghi vào thư mục làm việc của JVM trong production.
     */
    @NotBlank
    private String dir = "/var/eoffice/uploads/documents";

    @Positive
    @Max(104857600L) // Giới hạn tối đa 100 MB
    private long maxSizeBytes = 26214400L; // 25 MB default

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>(List.of("docx", "pdf"));

    @AssertTrue(message = "Đường dẫn thư mục tải lên phải là đường dẫn tuyệt đối, chuẩn hóa và không chứa '..'")
    public boolean isDirValid() {
        if (dir == null || dir.isBlank() || dir.contains("..")) {
            return false;
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(dir);
            boolean isAbs = path.isAbsolute() || dir.startsWith("/");
            if (!isAbs) {
                return false;
            }
            String trimmed = dir;
            while (trimmed.length() > 1 && (trimmed.endsWith("/") || trimmed.endsWith("\\"))) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            java.nio.file.Path trimmedPath = java.nio.file.Paths.get(trimmed);
            return trimmedPath.equals(trimmedPath.normalize());
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions == null ? Collections.emptyList() : Collections.unmodifiableList(allowedExtensions);
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions != null ? new ArrayList<>(allowedExtensions) : new ArrayList<>();
    }
}

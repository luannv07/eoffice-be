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
    private static final List<String> SENSITIVE_PREFIXES = List.of(
            "/etc", "/bin", "/sbin", "/usr", "/boot", "/root", "/proc", "/sys", "/lib", "/lib64", "/dev",
            "c:/windows", "c:/program files", "c:/program files (x86)", "c:/programdata"
    );
     /**
     * Đường dẫn tuyệt đối thư mục lưu trữ tài liệu.
     * Áp dụng chính sách chặn các tiền tố thư mục hệ thống nhạy cảm (Linux & Windows);
     * các đường dẫn ứng dụng đơn cấp như /uploads vẫn hợp lệ nếu không thuộc danh sách nhạy cảm.
     */
    @NotBlank
    private String dir = "/var/eoffice/uploads/documents";

    @Positive
    @Max(104857600L) // Giới hạn tối đa 100 MB
    private long maxSizeBytes = 26214400L; // 25 MB default

    @NotEmpty
    private List<@NotBlank String> allowedExtensions = new ArrayList<>(List.of("docx", "pdf"));

    @AssertTrue(message = "Đường dẫn thư mục tải lên phải là đường dẫn tuyệt đối, chuẩn hóa, không chứa '..' và không được là thư mục hệ thống nhạy cảm")
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
            // Từ chối đường dẫn gốc (root) hệ thống
            if ("/".equals(trimmed) || "\\".equals(trimmed) || trimmed.matches("^[a-zA-Z]:[/\\\\]?$")) {
                return false;
            }
            java.nio.file.Path trimmedPath = java.nio.file.Paths.get(trimmed);
            if (!trimmedPath.isAbsolute() && !trimmed.startsWith("/")) {
                return false;
            }
            java.nio.file.Path normalized = trimmedPath.normalize();
            if (!trimmedPath.equals(normalized)) {
                return false;
            }
            // Đảm bảo không trỏ tới thư mục hệ thống nhạy cảm (ví dụ: /etc, /etc/ssh, /bin, C:\Windows\System32),
            // kể cả các thư mục con của chúng — không dựa vào số lượng segment của đường dẫn.
            if (isSensitivePath(normalized)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private boolean isSensitivePath(java.nio.file.Path normalized) {
        String normalizedStr = normalized.toString().replace('\\', '/').toLowerCase();
        for (String prefix : SENSITIVE_PREFIXES) {
            if (normalizedStr.equals(prefix) || normalizedStr.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions == null ? Collections.emptyList() : Collections.unmodifiableList(allowedExtensions);
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions != null ? new ArrayList<>(allowedExtensions) : new ArrayList<>();
    }
}

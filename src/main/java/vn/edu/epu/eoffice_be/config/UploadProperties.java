package vn.edu.epu.eoffice_be.config;

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
    private long maxSizeBytes = 26214400L; // 25 MB

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>(List.of("docx", "pdf"));

    public List<String> getAllowedExtensions() {
        return allowedExtensions == null ? Collections.emptyList() : Collections.unmodifiableList(allowedExtensions);
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions != null ? new ArrayList<>(allowedExtensions) : new ArrayList<>();
    }
}

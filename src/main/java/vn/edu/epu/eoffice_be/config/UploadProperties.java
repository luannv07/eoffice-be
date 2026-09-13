package vn.edu.epu.eoffice_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "eoffice.upload")
@Getter
@Setter
public class UploadProperties {
    /**
     * Đường dẫn tuyệt đối thư mục lưu trữ tài liệu, đảm bảo không ghi vào thư mục làm việc của JVM trong production.
     */
    private String dir = "/var/eoffice/uploads/documents";

    private long maxSizeBytes = 26214400L; // 25 MB

    private List<String> allowedExtensions = new ArrayList<>(List.of("docx", "pdf"));
}

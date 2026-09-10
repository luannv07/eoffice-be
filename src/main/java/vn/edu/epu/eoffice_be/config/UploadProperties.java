package vn.edu.epu.eoffice_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "eoffice.upload")
@Getter
@Setter
public class UploadProperties {

    private String dir = "uploads/documents";

    private long maxSizeBytes = 26214400L; // 25 MB

    private List<String> allowedExtensions = List.of("docx", "pdf", "DOCX", "PDF");
}

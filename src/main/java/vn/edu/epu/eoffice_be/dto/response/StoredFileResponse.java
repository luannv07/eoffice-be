package vn.edu.epu.eoffice_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileResponse {

    private String id;
    private String originalFileName;
    private String contentType;
    private long sizeBytes;
    private Map<String, String> metadata;
}

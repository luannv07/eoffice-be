package vn.edu.epu.eoffice_be.storage.provider;

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
public class StoredFile {

    private String id;
    private String originalFileName;
    private String contentType;
    private long sizeBytes;
    private byte[] content;
    private Map<String, String> metadata;
}

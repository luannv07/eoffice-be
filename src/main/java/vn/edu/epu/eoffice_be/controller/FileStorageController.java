package vn.edu.epu.eoffice_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.epu.eoffice_be.common.annotation.Auditable;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;
import vn.edu.epu.eoffice_be.dto.response.StoredFileResponse;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.io.IOException;
import java.util.List;

import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;

@RestController
@RequestMapping("/file-storage")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("isAuthenticated()")
@Tag(name = "File Storage", description = "Quản lý tệp qua các backend lưu trữ có thể thay thế (Strategy pattern)")
public class FileStorageController {

    private final FileStorageProviderRegistry registry;
    private final StoredFileMetadataRepository metadataRepository;

    @Operation(summary = "Tải tệp lên backend lưu trữ (Create)")
    @PostMapping("/upload")
    public BaseResponse<StoredFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String provider) {
        FileStorageProvider p = resolve(provider);
        StoredFile saved = p.save(toStoredFile(file));
        try {
            StoredFileMetadata meta = StoredFileMetadata.builder()
                    .provider(p.getName())
                    .fileId(saved.getId())
                    .originalFileName(saved.getOriginalFileName())
                    .contentType(saved.getContentType())
                    .sizeBytes(saved.getSizeBytes())
                    .storagePath(saved.getMetadata() != null ? saved.getMetadata().get("storagePath") : null)
                    .metadata(saved.getMetadata())
                    .build();
            metadataRepository.save(meta);
        } catch (Exception e) {
            // bỏ qua nếu có lỗi lưu metadata không chặn luồng chính
        }
        return BaseResponse.success(toResponse(saved));
    }

    @Operation(summary = "Tải xuống nội dung tệp (Read)")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        StoredFile sf = resolve(provider).get(fileId);
        ByteArrayResource resource = new ByteArrayResource(sf.getContent());
        String name = sf.getOriginalFileName() != null ? sf.getOriginalFileName() : "file";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        sf.getContentType() != null ? sf.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(resource);
    }

    @Operation(summary = "Lấy siêu dữ liệu tệp (Read metadata)")
    @GetMapping("/{fileId}")
    public BaseResponse<StoredFileResponse> getMetadata(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        return BaseResponse.success(toResponse(resolve(provider).get(fileId)));
    }

    @Operation(summary = "Cập nhật nội dung tệp (Update)")
    @PutMapping("/{fileId}")
    public BaseResponse<StoredFileResponse> update(
            @PathVariable String fileId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String provider) {
        StoredFile updated = resolve(provider).update(fileId, toStoredFile(file));
        return BaseResponse.success(toResponse(updated));
    }

    @Operation(summary = "Xóa tệp (Delete)")
    @DeleteMapping("/{fileId}")
    public BaseResponse<Void> delete(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        resolve(provider).delete(fileId);
        return BaseResponse.success(null, "Xóa tệp thành công");
    }

    @Operation(summary = "Liệt kê toàn bộ tệp (GetAll)")
    @GetMapping
    public BaseResponse<List<StoredFileResponse>> getAll(
            @RequestParam(required = false) String provider) {
        List<StoredFileResponse> list = resolve(provider).getAll().stream()
                .map(this::toResponse)
                .toList();
        return BaseResponse.success(list);
    }

    @Operation(summary = "Lấy chi tiết tệp (GetDetail)")
    @GetMapping("/{fileId}/detail")
    public BaseResponse<StoredFileResponse> getDetail(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        return BaseResponse.success(toResponse(resolve(provider).getDetail(fileId)));
    }

    @Operation(summary = "Xóa nhiều tệp (DeleteMany)")
    @DeleteMapping("/batch")
    public BaseResponse<Void> deleteMany(
            @RequestBody List<String> fileIds,
            @RequestParam(required = false) String provider) {
        resolve(provider).deleteMany(fileIds);
        return BaseResponse.success(null, "Xóa nhiều tệp thành công");
    }

    private FileStorageProvider resolve(String provider) {
        return (provider == null || provider.isBlank())
                ? registry.getActiveProvider()
                : registry.getProvider(provider);
    }

    private StoredFile toStoredFile(MultipartFile file) {
        try {
            return StoredFile.builder()
                    .originalFileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .content(file.getBytes())
                    .build();
        } catch (IOException e) {
            throw new FileStorageException("Không thể đọc nội dung tệp tải lên", e);
        }
    }

    private StoredFileResponse toResponse(StoredFile sf) {
        return StoredFileResponse.builder()
                .id(sf.getId())
                .originalFileName(sf.getOriginalFileName())
                .contentType(sf.getContentType())
                .sizeBytes(sf.getSizeBytes())
                .metadata(sf.getMetadata())
                .build();
    }
}

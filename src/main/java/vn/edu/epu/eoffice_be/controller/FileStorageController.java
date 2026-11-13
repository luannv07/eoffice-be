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
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.response.StoredFileResponse;
import vn.edu.epu.eoffice_be.storage.provider.FileNotFoundStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProvider;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;
import vn.edu.epu.eoffice_be.storage.provider.InvalidFileStorageException;
import vn.edu.epu.eoffice_be.storage.provider.StoredFile;

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import vn.edu.epu.eoffice_be.entity.StoredFileMetadata;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;

@Slf4j
@RestController
@RequestMapping("/file-storage")
@RequiredArgsConstructor
@Auditable
@PreAuthorize("isAuthenticated()")
@Tag(name = "File Storage", description = "Quản lý tệp qua các backend lưu trữ có thể thay thế (Strategy pattern)")
public class FileStorageController {

    private final FileStorageProviderRegistry registry;
    private final StoredFileMetadataRepository metadataRepository;
    private final vn.edu.epu.eoffice_be.service.DocumentFileReadGuard documentFiles;

    @Operation(summary = "Tải tệp lên backend lưu trữ (Create)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<StoredFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String provider) {
        FileStorageProvider p = resolve(provider);
        StoredFile saved = p.save(toStoredFile(file));
        try {
            StoredFileMetadata meta = metadataRepository.findByProviderAndFileId(p.getName(), saved.getId())
                    .orElseGet(() -> StoredFileMetadata.builder()
                            .provider(p.getName())
                            .fileId(saved.getId())
                            .build());
            meta.setOriginalFileName(saved.getOriginalFileName());
            meta.setContentType(saved.getContentType());
            meta.setSizeBytes(saved.getSizeBytes());
            if (saved.getMetadata() != null && saved.getMetadata().containsKey("storagePath")) {
                meta.setStoragePath(saved.getMetadata().get("storagePath"));
            }
            if (saved.getMetadata() != null) {
                meta.setMetadata(saved.getMetadata());
            }
            metadataRepository.save(meta);
        } catch (Exception e) {
            log.error("Failed to save file metadata for fileId: {}", saved.getId(), e);
            try {
                p.delete(saved.getId());
            } catch (Exception deleteEx) {
                log.warn("Failed to rollback uploaded file for fileId: {}", saved.getId(), deleteEx);
            }
            throw new FileStorageException("Lưu siêu dữ liệu tệp thất bại", e);
        }
        return BaseResponse.success(toResponse(saved));
    }

    @Operation(summary = "Tải xuống nội dung tệp (Read)")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        documentFiles.assertRead(resolve(provider).getName(), fileId);
        StoredFile sf;
        try {
            sf = resolve(provider).get(fileId);
        } catch (FileStorageException ex) {
            BusinessException be = mapStorageException(fileId, ex);
            if (be != null) {
                throw be;
            }
            throw ex;
        }
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
        documentFiles.assertRead(resolve(provider).getName(), fileId);
        try {
            return BaseResponse.success(toResponse(resolve(provider).get(fileId)));
        } catch (FileStorageException ex) {
            BusinessException be = mapStorageException(fileId, ex);
            if (be != null) {
                throw be;
            }
            throw ex;
        }
    }

    @Operation(summary = "Cập nhật nội dung tệp (Update)")
    @PutMapping(value = "/{fileId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<StoredFileResponse> update(
            @PathVariable String fileId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String provider) {
        documentFiles.assertUnboundWrite(resolve(provider).getName(), fileId);
        StoredFile updated = resolve(provider).update(fileId, toStoredFile(file));
        return BaseResponse.success(toResponse(updated));
    }

    @Operation(summary = "Xóa tệp (Delete)")
    @DeleteMapping("/{fileId}")
    public BaseResponse<Void> delete(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        documentFiles.assertUnboundWrite(resolve(provider).getName(), fileId);
        try {
            resolve(provider).delete(fileId);
        } catch (FileStorageException ex) {
            BusinessException be = mapDeleteStorageException(fileId, ex);
            if (be != null) {
                throw be;
            }
            throw ex;
        }
        return BaseResponse.success(null, "Xóa tệp thành công");
    }

    @Operation(summary = "Liệt kê toàn bộ tệp (GetAll)")
    @GetMapping
    public BaseResponse<List<StoredFileResponse>> getAll(
            @RequestParam(required = false) String provider) {
        var storage = resolve(provider);
        var files = storage.getAll();
        var visible = documentFiles.visibleFileIds(storage.getName(), files.stream().map(StoredFile::getId).toList());
        List<StoredFileResponse> list = files.stream()
                .filter(file -> visible.contains(file.getId()))
                .map(this::toResponse)
                .toList();
        return BaseResponse.success(list);
    }

    @Operation(summary = "Lấy chi tiết tệp (GetDetail)")
    @GetMapping("/{fileId}/detail")
    public BaseResponse<StoredFileResponse> getDetail(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider) {
        documentFiles.assertRead(resolve(provider).getName(), fileId);
        try {
            return BaseResponse.success(toResponse(resolve(provider).getDetail(fileId)));
        } catch (FileStorageException ex) {
            BusinessException be = mapStorageException(fileId, ex);
            if (be != null) {
                throw be;
            }
            throw ex;
        } catch (UnsupportedOperationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Backend lưu trữ không hỗ trợ lấy chi tiết tệp", ex);
        }
    }

    @Operation(summary = "Xóa nhiều tệp (DeleteMany)")
    @DeleteMapping("/batch")
    public BaseResponse<Void> deleteMany(
            @RequestBody List<String> fileIds,
            @RequestParam(required = false) String provider) {
        if (fileIds == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Danh sách ID tệp không được để null.");
        }
        var storage = resolve(provider);
        var providerName = storage.getName();
        for (String fileId : fileIds) documentFiles.assertUnboundWrite(providerName, fileId);
        storage.deleteMany(fileIds);
        return BaseResponse.success(null, "Xóa nhiều tệp thành công");
    }

    private FileStorageProvider resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            return registry.getActiveProvider();
        }
        try {
            return registry.getProvider(provider);
        } catch (IllegalStateException e) {
            String sanitizedProvider = provider != null ? provider.replaceAll("[\\r\\n]", "_").trim() : "null";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Provider lưu trữ không hợp lệ: " + sanitizedProvider, e);
        }
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

    private String sanitizeId(String id) {
        return id != null ? id.replaceAll("[\\r\\n]", "_").trim() : "null";
    }

    private BusinessException mapStorageException(String fileId, FileStorageException ex) {
        if (ex instanceof FileNotFoundStorageException || ex.getErrorCode() == ErrorCode.NOT_FOUND) {
            return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp tin: " + sanitizeId(fileId), ex);
        }
        if (ex instanceof InvalidFileStorageException || ex.getErrorCode() == ErrorCode.BAD_REQUEST) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "Yêu cầu tệp không hợp lệ: " + sanitizeId(fileId), ex);
        }
        return null;
    }

    private BusinessException mapDeleteStorageException(String fileId, FileStorageException ex) {
        if (ex instanceof FileNotFoundStorageException || ex.getErrorCode() == ErrorCode.NOT_FOUND) {
            return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp tin để xóa: " + sanitizeId(fileId), ex);
        }
        if (ex instanceof InvalidFileStorageException || ex.getErrorCode() == ErrorCode.BAD_REQUEST) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "Yêu cầu tệp không hợp lệ: " + sanitizeId(fileId), ex);
        }
        return null;
    }
}

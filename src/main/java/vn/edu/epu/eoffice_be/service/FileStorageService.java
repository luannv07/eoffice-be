package vn.edu.epu.eoffice_be.service;

import org.springframework.core.io.Resource;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

import java.io.InputStream;

public interface FileStorageService {

    /**
     * Lưu trữ nội dung file dạng byte[] vào thư mục chỉ định.
     * Tên file sẽ được tự động gắn UUID để chống trùng lặp và tấn công Path Traversal.
     *
     * @param content          Dữ liệu file
     * @param originalFilename Tên gốc của file
     * @param subDirectory     Thư mục con (ví dụ: "drafts", "official")
     * @return Đường dẫn tương đối của file sau khi lưu
     */
    String storeFile(byte[] content, String originalFilename, String subDirectory);

    /**
     * Đọc toàn bộ nội dung file dạng byte[] từ đường dẫn tương đối.
     * <p>
     * Giới hạn kích thước file an toàn mặc định là 20MB (20.971.520 bytes, cấu hình qua
     * {@code eoffice.storage.max-file-size-bytes}) để tránh cạn kiệt bộ nhớ Heap (OutOfMemoryError).
     * Đối với các tệp lớn vượt quá ngưỡng này, hãy sử dụng luồng stream {@link #loadFileInputStream(String)}
     * hoặc Spring {@link #loadFileAsResource(String)}.
     * </p>
     *
     * @param relativePath Đường dẫn tương đối đã lưu của file
     * @return Mảng byte của file
     * @throws BusinessException nếu file vượt quá giới hạn an toàn 20MB (mã lỗi STORAGE_ERROR),
     *                           hoặc khi phát hiện đường dẫn không hợp lệ / lỗi I/O đọc file
     */
    byte[] loadFileBytes(String relativePath);

    /**
     * Mở luồng đọc InputStream từ đường dẫn tương đối (phù hợp với tệp lớn, tránh tràn bộ nhớ heap).
     *
     * @param relativePath Đường dẫn tương đối đã lưu
     * @return InputStream của file
     */
    InputStream loadFileInputStream(String relativePath);

    /**
     * Tải file dưới dạng Spring Resource để phục vụ stream/download qua HTTP cho file lớn.
     *
     * @param relativePath Đường dẫn tương đối đã lưu
     * @return Spring Resource của file
     */
    Resource loadFileAsResource(String relativePath);

    /**
     * Tính toán mã băm SHA-256 của file để kiểm tra tính toàn vẹn (DevSecOps).
     *
     * @param content Dữ liệu file
     * @return Chuỗi hex 64 ký tự của mã SHA-256
     */
    String computeSha256(byte[] content);
}
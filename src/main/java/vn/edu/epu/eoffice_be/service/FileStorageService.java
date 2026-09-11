package vn.edu.epu.eoffice_be.service;

import org.springframework.core.io.Resource;

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
     * Giới hạn kích thước file an toàn để tránh OutOfMemoryError.
     *
     * @param relativePath Đường dẫn tương đối đã lưu
     * @return Mảng byte của file
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
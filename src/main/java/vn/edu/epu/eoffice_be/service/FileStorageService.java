package vn.edu.epu.eoffice_be.service;

import org.springframework.core.io.Resource;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

import java.io.InputStream;

public interface FileStorageService {

    /**
     * Lưu trữ nội dung file dạng byte[] vào thư mục con chỉ định.
     * <p>
     * Tên file được tự động gắn UUID ngẫu nhiên để đảm bảo duy nhất và chống tấn công Path Traversal
     * từ tên file gốc. Để đảm bảo an toàn, tham số {@code subDirectory} phải là một tên thư mục đơn,
     * không được phép chứa các ký tự duyệt đường dẫn nguy hiểm (như {@code ".."}, {@code "/"} hay {@code "\\"}).
     * Các lớp triển khai bắt buộc phải sanitize hoặc kiểm tra (validate) nghiêm ngặt tham số này nhằm ngăn chặn
     * triệt để tấn công Path Traversal và Symbolic Link.
     * </p>
     *
     * @param content          Dữ liệu file
     * @param originalFilename Tên gốc của file
     * @param subDirectory     Tên thư mục con đơn, không chứa ký tự duyệt thư mục (ví dụ: "drafts", "official")
     * @return Đường dẫn tương đối của file sau khi lưu
     * @throws BusinessException nếu phát hiện tấn công Path Traversal hoặc gặp lỗi I/O khi lưu file
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
     * @return Chuỗi hex 64 ký tự của mã SHA-256, hoặc null nếu content là null
     */
    String computeSha256(byte[] content);

    /**
     * Tính toán mã băm SHA-256 từ luồng InputStream phục vụ kiểm tra tính toàn vẹn cho file lớn
     * theo cơ chế streaming, tránh nạp toàn bộ nội dung file vào bộ nhớ Heap.
     *
     * @param stream Luồng đọc dữ liệu của file
     * @return Chuỗi hex 64 ký tự của mã SHA-256, hoặc null nếu stream là null
     * @throws BusinessException nếu gặp lỗi I/O trong quá trình đọc luồng dữ liệu
     */
    String computeSha256(InputStream stream);

    /**
     * Xóa tệp tin đã lưu trữ theo đường dẫn tương đối.
     *
     * @param relativePath Đường dẫn tương đối của tệp tin cần xóa
     * @return true nếu xóa thành công, false nếu tệp không tồn tại hoặc có lỗi
     */
    boolean deleteFile(String relativePath);
}
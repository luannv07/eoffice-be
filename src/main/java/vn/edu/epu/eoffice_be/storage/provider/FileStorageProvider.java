package vn.edu.epu.eoffice_be.storage.provider;

import java.util.List;

/**
 * Hợp đồng chiến lược (Strategy) cho các backend lưu trữ tệp có thể thay thế.
 * Các provider mới (filesystem, s3, telegram, ...) triển khai interface này và
 * được chọn tại runtime thông qua {@link FileStorageProviderRegistry}.
 */
public interface FileStorageProvider {

    String getName();

    StoredFile save(StoredFile file);

    /**
     * Upload for a document transaction. The returned receipt identifies this
     * upload for rollback cleanup; the caller owns metadata persistence.
     */
    default StoredFile saveForDocument(StoredFile file) {
        return save(file);
    }

    /** Discard only the upload identified by the receipt, without deleting DB metadata. */
    default void discardUpload(StoredFile receipt) {
        delete(receipt.getId());
    }

    StoredFile get(String fileId);

    StoredFile update(String fileId, StoredFile file);

    void delete(String fileId);

    /**
     * Liệt kê toàn bộ tệp đã lưu. Các backend không có mô hình thư mục
     * (ví dụ Telegram) không hỗ trợ — triển khai có thể trả về danh sách rỗng.
     */
    List<StoredFile> getAll();

    /**
     * Lấy siêu dữ liệu mở rộng của một tệp. Các backend không thể liệt kê
     * đối tượng đã lưu không hỗ trợ — triển khai ném
     * {@link UnsupportedOperationException}.
     */
    StoredFile getDetail(String fileId);

    /**
     * Xóa hàng loạt. Các backend thiếu khả năng liệt kê/siêu dữ liệu không
     * hỗ trợ — triển khai ném {@link UnsupportedOperationException}.
     */
    void deleteMany(List<String> fileIds);
}

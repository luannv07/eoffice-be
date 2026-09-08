# 📚 Nhóm Chức Năng 2: Danh Mục & Sổ Văn Bản (UC05 - UC07)

Tài liệu này tổng hợp hiện trạng, các thành phần còn thiếu và hướng dẫn kỹ thuật triển khai cho nhóm **Danh Mục & Sổ Văn Bản**.

---

## 1. Chi Tiết Từng Use Case

### 1.1. UC05: Quản lý loại văn bản
- **Tác nhân:** Cán bộ văn thư.
- **Hiện trạng:** 🟢 **Hoàn thành 100%**
  - ✅ **Đã hoàn thành:**
    - Sử dụng hệ thống danh mục dùng chung `DictionaryItem` (`type_cd = 'DOCUMENT_TYPE'`).
    - `DictionaryItemController` và `DictionaryItemService` đã hỗ trợ đầy đủ:
      - Xem danh sách loại văn bản (`GET /api/v1/dictionary-items/by-type/DOCUMENT_TYPE`).
      - Tìm kiếm, lọc và phân trang.
      - Xem chi tiết (`GET /api/v1/dictionary-items/{id}`).
      - Thêm mới (`POST /api/v1/dictionary-items`).
      - Chỉnh sửa thông tin (`PUT /api/v1/dictionary-items/{id}`).
      - Xóa mềm / Bật tắt trạng thái kích hoạt.
  - ❌ **Chức năng còn thiếu:** Không có.
  - 🎯 **Theo core của thầy:**
    - Loại văn bản phải gắn trực tiếp với Sổ Văn Bản: mỗi loại văn bản (Quyết định, Nghị quyết, Thông báo, Chỉ thị...) có sổ riêng. Cần đảm bảo `DictionaryItem` với `type_cd = 'DOCUMENT_TYPE'` được dùng làm `document_type_id` khóa ngoại trong `DocumentBook` và `Document`.

---

### 1.2. UC06: Quản lý sổ văn bản
- **Tác nhân:** Cán bộ văn thư.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Xem danh sách sổ văn bản` (theo năm, theo đơn vị/phòng ban, theo loại sổ, theo cấp).
    - `[extend] Tìm kiếm và lọc sổ văn bản`.
    - `[extend] Xem chi tiết sổ văn bản` (số hiện tại, số bắt đầu, quy tắc sinh số, loại văn bản, đơn vị sở hữu).
    - `[extend] Tạo mới sổ văn bản`.
    - `[extend] Chỉnh sửa / Đóng sổ văn bản`.
  - 🎯 **Theo core của thầy (Sổ Văn Bản 2 cấp):**
    - **Sổ Văn Bản phải có 2 cấp rõ ràng theo đặc tả đề tài:**
      - **Cấp 1 (Sổ của Đơn vị/Trường):** Dùng cho văn bản ban hành ở cấp Đơn vị (Quyết định Trường, Thông báo Trường...). Số hiệu dạng: `125/QĐ-ĐHEPU`.
      - **Cấp 2 (Sổ của Phòng/Ban trực thuộc):** Dùng cho văn bản nội bộ Phòng/Ban. Số hiệu dạng: `45/TB-CNTT`.
    - Entity `DocumentBook` cần bổ sung trường `book_level` (1 hoặc 2) và liên kết với cả `org_node_id` (đơn vị sở hữu sổ) và `document_type_id` (mỗi loại văn bản có sổ riêng).
    - **Cronjob tự động reset:** Vào ngày 01/01 hàng năm, tự động đóng sổ cũ (`is_closed = true`) và tạo bộ sổ mới cho năm mới với `current_number = 0`.

---

### 1.3. UC07: Cấp số văn bản
- **Tác nhân:** Cán bộ văn thư.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Kiểm tra trạng thái văn bản` (chỉ cấp số khi văn bản đã được duyệt/ký phát hành - trạng thái `APPROVED`).
    - `[include] Chọn sổ văn bản phù hợp` (theo loại văn bản và đơn vị ban hành).
    - `[include] Sinh số văn bản tự động tăng dần và thread-safe`.
    - `[include] Ghi nhận ngày hiệu lực & Lưu thông tin cấp số`.
    - `[extend] Hủy cấp số` (khi văn bản bị thu hồi hoặc cấp sai).
    - `[extend] Thông báo chưa đủ điều kiện cấp số`.
  - 🎯 **Theo core của thầy (Quy tắc số văn bản đúng thực tế):**
    - **Số văn bản phải phân biệt theo cấp sổ:**
      - Cấp 1: `{STT}/{MÃ_LOẠI}-{MÃ_ĐƠN_VỊ}` → Ví dụ: `125/QĐ-ĐHEPU`
      - Cấp 2: `{STT}/{MÃ_LOẠI}-{MÃ_PHÒNG_BAN}` → Ví dụ: `45/TB-CNTT`
    - **Số bắt đầu từ 1 (không phải 0)** và **reset về 0 khi sang năm Dương lịch mới** (bộ đếm mới bắt đầu từ 1 của năm đó).
    - Cần dùng `Pessimistic Lock` (hoặc `@Version` Optimistic Lock) khi tăng `current_number` để đảm bảo không trùng số khi nhiều văn thư thao tác đồng thời.

---

## 2. Hướng Dẫn Kỹ Thuật Hoàn Thiện Nhóm Chức Năng

### Bước 1: Thiết kế Entity `DocumentBook` chuẩn 2 cấp
```java
@Entity
@Table(name = "document_books")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DocumentBook extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    private Long id;

    @Column(name = "book_code", nullable = false, unique = true, length = 50)
    private String bookCode; // VD: SVD-CNTT-2026

    @Column(name = "book_name", nullable = false, length = 255)
    private String bookName; // VD: Sổ Quyết định Khoa CNTT năm 2026

    @Column(name = "year", nullable = false)
    private Integer year; // 2026

    @Column(name = "book_level", nullable = false)
    private Integer bookLevel; // 1: Cấp Đơn vị/Trường, 2: Cấp Phòng/Ban

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId; // FK -> dictionary_items (DOCUMENT_TYPE)

    @Column(name = "org_node_id", nullable = false)
    private Long orgNodeId; // Đơn vị sở hữu sổ

    @Column(name = "current_number", nullable = false)
    private Integer currentNumber = 0; // Số hiện tại đã cấp

    @Column(name = "number_pattern", length = 100)
    private String numberPattern;
    // Cấp 1: "{NUM}/QĐ-ĐHEPU"  -> "125/QĐ-ĐHEPU"
    // Cấp 2: "{NUM}/TB-CNTT"   -> "45/TB-CNTT"

    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    @Column(name = "delete_flg", nullable = false)
    private Boolean deleteFlg = false;
}
```

### Bước 2: Logic Sinh Số Thread-safe (Pessimistic Lock)
```java
@Transactional
public String generateDocumentNumber(Long bookId) {
    // Pessimistic Write Lock: đảm bảo không trùng số dù nhiều request đồng thời
    DocumentBook book = documentBookRepository.findByIdForUpdate(bookId)
        .orElseThrow(() -> new BusinessException("Sổ văn bản không tồn tại hoặc đã đóng"));

    if (Boolean.TRUE.equals(book.getIsClosed())) {
        throw new BusinessException("Sổ văn bản đã đóng, không thể cấp số!");
    }

    int nextNumber = book.getCurrentNumber() + 1;
    book.setCurrentNumber(nextNumber);
    documentBookRepository.save(book);

    // Áp dụng pattern: "{NUM}/QĐ-ĐHEPU" -> "125/QĐ-ĐHEPU"
    return book.getNumberPattern().replace("{NUM}", String.valueOf(nextNumber));
}
```

### Bước 3: Cronjob Reset Sổ Văn Bản Đầu Năm Mới
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentBookYearResetJob {
    private final DocumentBookRepository documentBookRepository;

    // Chạy vào 00:00:00 ngày 01 tháng 01 hàng năm
    @Scheduled(cron = "0 0 0 1 1 *")
    @Transactional
    public void resetAllBookCounters() {
        int newYear = LocalDate.now().getYear();
        log.info("[CRON] Khởi tạo sổ văn bản cho năm mới: {}", newYear);

        List<DocumentBook> activeBooks = documentBookRepository
            .findAllByDeleteFlgFalseAndIsClosedFalse();

        activeBooks.forEach(oldBook -> {
            // Đóng sổ năm cũ (bảo toàn lịch sử, không xóa)
            oldBook.setIsClosed(true);
            documentBookRepository.save(oldBook);

            // Tạo sổ mới cho năm mới với current_number = 0
            DocumentBook newBook = new DocumentBook();
            newBook.setBookCode(oldBook.getBookCode().replace(
                String.valueOf(newYear - 1), String.valueOf(newYear)));
            newBook.setBookName(oldBook.getBookName().replace(
                String.valueOf(newYear - 1), String.valueOf(newYear)));
            newBook.setYear(newYear);
            newBook.setBookLevel(oldBook.getBookLevel());
            newBook.setDocumentTypeId(oldBook.getDocumentTypeId());
            newBook.setOrgNodeId(oldBook.getOrgNodeId());
            newBook.setNumberPattern(oldBook.getNumberPattern());
            newBook.setCurrentNumber(0);
            newBook.setIsClosed(false);
            documentBookRepository.save(newBook);
        });
    }
}
```

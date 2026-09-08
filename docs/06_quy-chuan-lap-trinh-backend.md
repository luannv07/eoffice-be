# 📐 06. Quy Chuẩn Lập Trình Backend (Coding Guidelines & Conventions)

Tài liệu này quy định các chuẩn mực lập trình bắt buộc khi phát triển tính năng mới trong dự án **`eoffice-be`**. Tất cả
thành viên mới cần đọc kỹ và tuân thủ.

---

## 1. Chuẩn Hóa Định Dạng Dữ Liệu Trả Về (API Response)

Mọi API trả về cho client đều phải được bọc trong lớp [
`BaseResponse<T>`](file:///d:/Visual%20studio%20code/eoffice-be/src/main/java/vn/edu/epu/eoffice_be/common/dto/BaseResponse.java):

### 1.1 Phản hồi thành công:

```java
// Trả về đối tượng
return BaseResponse.success(data);

// Trả về không có nội dung (Void)
return BaseResponse.

success(null);
```

**JSON trả về:**

```json
{
  "code": "OK",
  "message": "Thành công",
  "data": {
    ...
  }
}
```

### 1.2 Phản hồi lỗi:

Được xử lý tự động qua [
`GlobalExceptionHandler`](file:///d:/Visual%20studio%20code/eoffice-be/src/main/java/vn/edu/epu/eoffice_be/common/exception/GlobalExceptionHandler.java):

```json
{
  "code": "USERNAME_ALREADY_EXISTS",
  "message": "Tên đăng nhập đã tồn tại trong hệ thống.",
  "data": null
}
```

---

## 2. Quy Chuẩn Xử Lý Ngoại Lệ (Exception Handling)

> [!IMPORTANT]
> **Quy tắc vàng:** Không bao giờ ném ngoại lệ chung chung (`throw new RuntimeException(...)`). Luôn sử dụng [
`BusinessException`](file:///d:/Visual%20studio%20code/eoffice-be/src/main/java/vn/edu/epu/eoffice_be/common/exception/BusinessException.java)
> đi kèm với mã lỗi trong [
`ErrorCode`](file:///d:/Visual%20studio%20code/eoffice-be/src/main/java/vn/edu/epu/eoffice_be/common/enums/ErrorCode.java).

### 2.1 Định nghĩa mã lỗi mới trong `ErrorCode.java`:

```java

@Getter
public enum ErrorCode {
	// ... các mã cũ
	DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Văn bản không tồn tại hoặc đã bị xóa"),
	DOCUMENT_ALREADY_APPROVED(HttpStatus.BAD_REQUEST, "DOCUMENT_ALREADY_APPROVED", "Văn bản này đã được phê duyệt trước đó");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
```

### 2.2 Ném ngoại lệ trong tầng Service:

```java
Document doc = documentRepository.findByIdAndDeleteFlgFalse(documentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

if(doc.

isApproved()){
				throw new

BusinessException(ErrorCode.DOCUMENT_ALREADY_APPROVED);
}
```

---

## 3. Quy Chuẩn Phân Tách DTO & Entity

1. **Tuyệt đối không nhận trực tiếp Entity trong Request Body của Controller.**
    - Tạo DTO riêng trong gói `dto.request` (ví dụ: `CreateDocumentRequest`).
    - Khai báo các annotation validate: `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`.
2. **Tuyệt đối không trả trực tiếp Entity ra ngoài Response Body.**
    - Tạo DTO riêng trong gói `dto.response` (ví dụ: `DocumentResponse`).
    - Sử dụng Builder hoặc Mapper để chuyển đổi Entity -> DTO trước khi trả về.

---

## 4. Quy Chuẩn Tầng Repository

1. **Luôn lọc xóa mềm:**
   ```java
   // ĐÚNG:
   Optional<Document> findByIdAndDeleteFlgFalse(Long id);
   Page<Document> findByOrgNodeIdAndDeleteFlgFalse(Long orgNodeId, Pageable pageable);

   // SAI (Không được dùng vì có thể lấy phải bản ghi đã xóa):
   Optional<Document> findById(Long id);
   ```
2. **Truy vấn phân trang:** Kế thừa `JpaRepository<T, ID>` và sử dụng `Pageable`, `Page<T>`.

---

## 5. Quy Chuẩn Viết Unit Test

1. Sử dụng **JUnit 5 (`org.junit.jupiter.api.*`)** và **Mockito (`org.mockito.*`)**.
2. Đặt tên hàm test rõ ràng theo mẫu: `methodName_Condition_ExpectedResult`:
   ```java
   @Test
   @DisplayName("Đăng ký thất bại khi trùng tên đăng nhập")
   void register_WhenUsernameExists_ShouldThrowBusinessException() {
       // Given (Chuẩn bị dữ liệu)
       when(userLoginRepository.existsByUsernameAndDeleteFlgFalse("alice")).thenReturn(true);

       // When & Then (Thực thi & Kiểm tra)
       BusinessException ex = assertThrows(BusinessException.class, 
           () -> authService.register(request, "127.0.0.1"));
       assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
   }
   ```

---

## 6. Quy Trình Làm Việc Với Git (Git Workflow)

1. **Quy tắc đặt tên nhánh (Branch naming):**
    - Tính năng mới: `feat/ten-tinh-nang` (ví dụ: `feat/document-management`).
    - Sửa lỗi: `fix/ten-loi` (ví dụ: `fix/jwt-expiration-check`).
    - Tối ưu mã / cấu hình: `chore/noi-dung` hoặc `refactor/noi-dung`.
2. **Quy tắc commit message:**
    - `feat: thêm API tạo văn bản đi`
    - `fix: sửa lỗi sai timezone khi lưu audit log`
    - `docs: bổ sung tài liệu hướng dẫn starter kit`

---

🎉 **Chúc bạn phát triển dự án `eoffice-be` hiệu quả và thuận lợi!**

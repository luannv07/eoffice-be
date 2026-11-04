# BÀI HỌC KINH NGHIỆM TỪ ĐỢT RÀ SOÁT KIẾN TRÚC & AN TOÀN HỆ THỐNG EOFFICE-BE

> **Tài liệu tham chiếu chi tiết**: [bao-cao-tong-hop-lo-hong-va-kien-truc.md](file:///d:/Visual%20studio%20code/eoffice-be/docs/bao-cao-tong-hop-lo-hong-va-kien-truc.md)  
> **Dự án**: E-Office Backend (Spring Boot 3, Java 21, Liquibase, PostgreSQL, PKI)  
> **Mục tiêu**: Đúc kết các bài học cốt lõi trong thiết kế kiến trúc phần mềm, bảo mật chữ ký số PKI, kiểm soát trạng thái (State Machine) và quản trị dữ liệu.

---

## 1. BÀI HỌC VỀ THIẾT KẾ KIẾN TRÚC (ARCHITECTURE & DOMAIN MODEL)

### Bài học 1: Tuyệt đối tránh phát triển theo nhánh tính năng cô lập (Feature-in-Isolation)
* **Thực trạng**: Các lập trình viên (hoặc các AI agent) phát triển độc lập các module `draft`, `approval`, `workflow`, `publish`, `book` mà không có bản thiết kế Domain Model chung.
* **Hậu quả**:
  * Tồn tại 2 cột cùng ý nghĩa trong bảng `documents`: `creator_user_id` và `drafter_id`; `issuing_org_id` và `issuing_org_node_id`.
  * Phải dùng hook `@PrePersist` để chắp vá gán chéo dữ liệu, nhưng các câu truy vấn JPQL vẫn bị lệch.
* **Quy chuẩn rút ra**:
  * Trước khi viết code tính năng mới, bắt buộc phải định nghĩa **Single Source of Truth** cho thực thể chính (`Document`).
  * Mọi quan hệ thực thể phải được đồng thuận và quy chuẩn qua 1 file schema migration duy nhất, cấm tự tạo cột song song để "tiện việc code riêng".

### Bài học 2: Một thực thể chỉ được phép có DUY NHẤT MỘT động cơ quản lý vòng đời (Single Workflow Engine)
* **Thực trạng**: Hệ thống cùng lúc chạy 2 service điều khiển trạng thái văn bản:
  * `DocumentApprovalService`: tự gán `setStatus()`, ghi bảng `document_process_logs`, dùng `DocumentApprovalRequest`.
  * `DocumentWorkflowService`: dùng `WorkflowEngine`, ghi bảng `document_workflow_logs`.
* **Hậu quả**:
  * Bảng `document_process_logs` là write-only (chỉ ghi không ai đọc).
  * Lịch sử phê duyệt trên màn hình người dùng bị cụt, mất các bước gửi duyệt và thu hồi.
  * Bản ghi `DocumentApprovalRequest` bị treo ở `PENDING` vĩnh viễn dù văn bản đã được Hiệu trưởng duyệt và phát hành.
* **Quy chuẩn rút ra**:
  * Toàn bộ chuyển đổi trạng thái (`SUBMIT`, `REVIEW`, `APPROVE`, `REJECT`, `WITHDRAW`, `PUBLISH`, `REVOKE`) bắt buộc phải đi qua **một WorkflowEngine tập trung**.
  * Cấm các Service tự ý gọi `document.setStatus(...)` trực tiếp ngoài State Machine.

---

## 2. BÀI HỌC VỀ BẢO MẬT VÀ PHÂN QUYỀN (SECURITY & BFLA)

### Bài học 3: Không để sót Endpoint "cửa sau" (Backdoor / Broken Functional Level Authorization)
* **Thực trạng**: State Machine ở `WorkflowEngine` kiểm tra rất kỹ các bước chuyển tiếp từ `IN_REVIEW -> PENDING_APPROVAL -> APPROVED -> PUBLISHED`. Tuy nhiên, tồn tại một endpoint cũ `POST /documents/{id}/publish` trong `DocumentPublishController` cho phép ban hành thẳng từ `DRAFT`.
* **Quy chuẩn rút ra**:
  * Khi nâng cấp luồng nghiệp vụ lên State Machine mới, phải **deprecate và đóng triệt để các endpoint bypass cũ**, hoặc refactor endpoint cũ ủy quyền vào State Machine mới.
  * Mọi endpoint thay đổi trạng thái tài liệu phải chạy qua cùng một bộ guards.

### Bài học 4: Tránh đòn "tự sát" khi xử lý nghiệp vụ PKI / CRL
* **Thực trạng**: Khi người dùng gọi chức năng "Thu hồi văn bản" (`revokeDocument`), code backend lại gọi `pdfSignatureVerifier.revokeSerial(...)` để thêm serial của chứng thư số người ký vào file `revoked.json` (CRL).
* **Hậu quả**: Toàn bộ chứng thư số của Hiệu trưởng, Chuyên viên và Văn thư bị hủy bỏ trên toàn hệ thống. Mọi văn bản khác họ từng ký từ trước đến nay đều bị báo lỗi chữ ký không hợp lệ!
* **Quy chuẩn rút ra**:
  * Phân biệt rạch ròi:
    1. **Thu hồi văn bản hành chính (Document Revocation)**: Chỉ là thay đổi trạng thái nghiệp vụ của văn bản (`status = REVOKED`). Chữ ký số trên văn bản tại thời điểm ký vẫn hoàn toàn hợp lệ.
    2. **Thu hồi chứng thư số (Certificate Revocation - CRL/OCSP)**: Chỉ xảy ra khi Khóa bí mật (Private Key) của người ký bị lộ, bị đánh cắp hoặc người đó thôi việc.

### Bài học 5: Đảm bảo tính toàn vẹn của Security Context trong JWT Token
* **Thực trạng**: Trong `ProfileService.completeProfile`, sau khi cập nhật hồ sơ, token mới sinh ra bị quên truyền `orgId`, khiến user bị 403 Forbidden trên toàn bộ hệ thống sau khi lưu profile.
* **Quy chuẩn rút ra**:
  * Tập trung hóa logic tạo Token vào một method duy nhất trong `AuthService` có nạp đầy đủ User Context (`userId`, `username`, `role`, `orgId`, `permissions`).
  * Không tạo các overload tùy tiện làm rơi rụng claims bảo mật quan trọng.

---

## 3. BÀI HỌC VỀ DỮ LIỆU & DATABASE CONSTRAINTS

### Bài học 6: Không dùng Unique Index ở Database để gượng ép quy tắc nghiệp vụ đời thực
* **Thực trạng**: Changelog `2026-10-23_fix-position-assignment-limits-and-seed-clerk.yml` tạo unique index `(org_node_id, position_cd)` ngoại trừ `GV`.
* **Hậu quả**: Hệ thống chặn không cho phân công 2 Phó Hiệu trưởng (PHT), 2 Phó Trưởng khoa (PK), 2 Phó Trưởng phòng (PP), gây lỗi sập database 500 khi ban giám hiệu có nhiều cấp phó.
* **Quy chuẩn rút ra**:
  * Các quy tắc mang tính tổ chức hành chính (số lượng cấp phó, chuyên viên) phải được định nghĩa trong cấu hình nghiệp vụ (Business Rule / Configuration Table), không được hardcode bằng UNIQUE partial index trong database nếu mô hình đời thực cho phép nhiều người cùng giữ một vai trò.

### Bài học 7: Cấm dùng Reflection trên chính DTO nội bộ
* **Thực trạng**: `DocumentPublishService` dùng reflection tìm getter `getTargetIds` hoặc `getOrgIds`, trong khi trường DTO tên là `recipientOrgIds`. Kết quả là danh sách đơn vị nhận bị bỏ qua âm thầm (Silent Data Loss).
* **Quy chuẩn rút ra**:
  * Luôn sử dụng code typed chuẩn (compile-time type safety).
  * Nghiêm cấm dùng Reflection để "đoán" tên trường của chính DTO trong cùng một project.

### Bài học 8: Chuẩn hóa múi giờ và kiểu dữ liệu ngày tháng
* **Thực trạng**: JVM chạy `UTC` nhưng code lại trộn lẫn `ZoneId.systemDefault()`, `Asia/Ho_Chi_Minh` và `Document.getBusinessZone()`. Cột `issue_date` là `DATE`, còn `effective_date` là `TIMESTAMPTZ`.
* **Hậu quả**: Văn bản ký duyệt trong khoảng 00:00 - 07:00 sáng bị lệch sang ngày hôm trước, và đêm giao thừa bị nhảy năm gây lỗi cấp số sổ văn bản.
* **Quy chuẩn rút ra**:
  * Toàn bộ Database và Entity Java lưu trữ theo chuẩn `Instant` (UTC).
  * Mọi quy đổi sang ngày giờ địa phương (`Asia/Ho_Chi_Minh`) chỉ thực hiện ở tầng Presentation/DTO Mapper với múi giờ được cấu hình tập trung.

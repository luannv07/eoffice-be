# 📋 05. Hệ Thống Audit & Nhật Ký Biến Động (Auditing & History Logs)

Tài liệu này hướng dẫn cách sử dụng và cơ chế hoạt động của hệ thống ghi vết truy cập (User Access Audit) và lịch sử
biến động dữ liệu (Entity Change History) trong **`eoffice-be`**.

---

## 1. Mục Tiêu Của Hệ Thống Audit

Trong các hệ thống hành chính công văn và hồ sơ số:

1. **Tính bất biến & minh bạch:** Mọi thao tác xem, sửa, tạo, xóa tài liệu quan trọng đều phải được ghi lại vết truy vết
   rõ ràng.
2. **Không làm chậm hệ thống:** Quá trình ghi log không được cản trở hoặc làm tăng thời gian phản hồi (latency) của API
   chính đối với người dùng cuối.
3. **Lưu trữ dữ liệu dạng cấu trúc:** Sử dụng định dạng `JSONB` của PostgreSQL để lưu ảnh chụp dữ liệu (Before/After
   Data) phục vụ đối soát và khôi phục khi cần.

---

## 2. Kiến Trúc 3 Tầng Kiểm Toán

```mermaid
graph TD
    Request[HTTP Request] --> Controller[Controller Method]
    
    subgraph "1. Tầng Controller Audit (@Auditable)"
        Controller -->|AOP Pointcut| ControllerAspect[ControllerAuditAspect]
        ControllerAspect -->|@Async Event| AsyncService[AuditLogAsyncService]
        AsyncService -->|Lưu vết truy cập| UserAuditLogTable[(user_audit_logs)]
    end

    subgraph "2. Tầng Entity Change Tracking (@TrackChange)"
        Controller --> ServiceMethod[Service Method]
        ServiceMethod -->|AOP Pointcut| ChangeAspect[EntityChangeAuditAspect]
        ChangeAspect -->|JSON Diff & Snapshot| ChangeLogTable[(change_history_logs)]
    end

    subgraph "3. Tầng JPA Auditing (BaseEntity)"
        ServiceMethod --> Repository[JPA Repository.save]
        Repository -->|Auto Fill Fields| BaseFields[created_by, created_date, last_modified_by, last_modified_date]
    end
```

---

## 3. Chi Tiết Từng Phân Hệ

### 3.1 Nhật Ký Truy Cập Người Dùng (`@Auditable` + `UserAuditLog`)

#### Cách sử dụng trong mã nguồn:

Gắn annotation `@Auditable` lên đầu class Controller hoặc từng method cụ thể:

```java
@RestController
@RequestMapping("/documents")
@Auditable // Tự động ghi nhận mọi request gửi tới controller này
public class DocumentController {
    // ...
}
```

#### Dữ liệu được ghi lại:

- `username`: Tên người thực hiện (hoặc `anonymous` nếu chưa đăng nhập).
- `client_ip`: IP của client (tự động nhận diện qua reverse proxy `X-Forwarded-For`).
- `user_agent`: Thông tin trình duyệt/thiết bị của client.
- `request_uri`: Đường dẫn API được gọi.
- `method_name`: Tên hàm trong Java xử lý request.
- `execution_time_ms`: Thời gian thực thi API (tính bằng mili-giây).
- `param_content`: Toàn bộ body/tham số gửi lên (được format JSON).

---

### 3.2 Lịch Sử Biến Động Dữ Liệu (`@TrackChange` + `ChangeHistoryLog`)

#### Cách sử dụng trong mã nguồn:

Gắn annotation `@TrackChange` lên method Service thực hiện thay đổi dữ liệu:

```java
@Service
public class DocumentService {

    @TrackChange(tableName = "documents", action = "UPDATE")
    @Transactional
    public DocumentResponse updateDocument(Long id, UpdateDocumentRequest request) {
        // Xử lý cập nhật
    }
}
```

#### Dữ liệu được ghi lại:

- `table_name`: Tên bảng bị tác động (ví dụ: `documents`, `user_infos`).
- `object_id`: Khóa chính (ID) của bản ghi bị thay đổi.
- `action`: Hành động (`INSERT`, `UPDATE`, `DELETE`).
- `changed_by`: Người thực hiện thay đổi.
- `changed_at`: Thời điểm diễn ra thay đổi.
- `before_data`: Ảnh chụp dữ liệu trước khi sửa (định dạng `JSONB`).
- `after_data`: Ảnh chụp dữ liệu sau khi sửa (định dạng `JSONB`).
- `changed_fields`: Danh sách các trường cụ thể có sự thay đổi giá trị (định dạng `JSONB`).

---

### 3.3 JPA Auditing Tự Động (BaseEntity)

Được cấu hình thông qua [
`JpaAuditingConfig.java`](file:///d:/Visual%20studio%20code/eoffice-be/src/main/java/vn/edu/epu/eoffice_be/common/config/JpaAuditingConfig.java):

- Bất kỳ thực thể nào kế thừa [
  `BaseEntity`](file:///d:/Visual%20studio%20code/eoffice-be/src/main/java/vn/edu/epu/eoffice_be/common/entity/BaseEntity.java)
  khi được gọi qua `.save()` sẽ tự động được Spring Data JPA điền:
    - `created_by` & `created_date`: Khi tạo mới lần đầu.
    - `last_modified_by` & `last_modified_date`: Khi cập nhật bản ghi.
- Người dùng hiện tại được lấy trực tiếp từ `SecurityContextHolder.getContext().getAuthentication().getName()`.

---

## 4. API Tra Cứu Nhật Ký Kiểm Toán (Dành Cho Quản Trị Viên)

Hệ thống đã cung cấp sẵn 2 API kèm phân trang, lọc nâng cao:

### 4.1 Tra cứu lịch sử truy cập API:

- **Endpoint:** `GET /api/v1/audit-logs`
- **Bộ lọc:** `username`, `clientIp`, `requestUri`, `fromTime`, `toTime`, `page`, `size`, `sort`.

### 4.2 Tra cứu lịch sử biến động dữ liệu:

- **Endpoint:** `GET /api/v1/change-history-logs`
- **Bộ lọc:** `tableName`, `objectId`, `action`, `changedBy`, `fromTime`, `toTime`, `page`, `size`, `sort`.

---

👉 **Đọc tiếp:** [06. Quy Chuẩn Lập Trình Backend](./06_quy-chuan-lap-trinh-backend.md)

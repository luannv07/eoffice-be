# 🗄️ 02. Cơ Sở Dữ Liệu & Thực Thể (JPA Entities)

Tài liệu này mô tả chi tiết lược đồ quan hệ cơ sở dữ liệu (ERD), danh sách các bảng và các thực thể JPA trong **`eoffice-be`**.

---

## 1. Sơ Đồ Thực Thể Quan Hệ (ERD)

```mermaid
erDiagram
    user_logins ||--o| user_infos : "1 - 1"
    user_logins ||--o{ position_assignments : "1 - N"
    org_nodes ||--o{ position_assignments : "1 - N"
    org_nodes ||--o{ org_node_closures : "ancestor/descendant"
    user_logins ||--o{ refresh_tokens : "1 - N"
    position_assignments ||--o{ position_degs : "ủy quyền"

    user_logins {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR role
        VARCHAR user_status_cd
        BOOLEAN must_change_password
        TIMESTAMP password_expired_at
        INT failed_login_attempts
        TIMESTAMP lockout_until
        TIMESTAMP last_login_at
        BOOLEAN delete_flg
    }

    user_infos {
        BIGINT id PK
        BIGINT user_login_id FK
        VARCHAR full_name
        DATE date_of_birth
        VARCHAR gender
        VARCHAR national_id_number
        VARCHAR avatar_url
        VARCHAR contact_email
        VARCHAR contact_phone
        VARCHAR employee_code
        VARCHAR academic_title
        VARCHAR academic_degree
        BOOLEAN delete_flg
    }

    org_nodes {
        BIGINT id PK
        BIGINT parent_id FK
        VARCHAR code UK
        VARCHAR name_vn
        VARCHAR name_en
        VARCHAR type_cd
        INT org_order
        BOOLEAN delete_flg
    }

    org_node_closures {
        BIGINT ancestor_id PK,FK
        BIGINT descendant_id PK,FK
        INT depth
    }

    position_assignments {
        BIGINT id PK
        BIGINT user_login_id FK
        BIGINT org_node_id FK
        VARCHAR position_cd
        TIMESTAMP effective_from
        TIMESTAMP effective_to
        TIMESTAMP actual_dismissal_date
        BOOLEAN delete_flg
    }

    position_permissions {
        BIGINT id PK
        VARCHAR position_cd
        VARCHAR permission_cd
        BOOLEAN inherit_to_children
        BOOLEAN delete_flg
    }

    role_permissions {
        BIGINT id PK
        VARCHAR role
        VARCHAR permission_cd
        BOOLEAN delete_flg
    }

    refresh_tokens {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR rt UK
        TIMESTAMP expires_at
        BOOLEAN is_revoked
    }

    dictionary_items {
        BIGINT id PK
        VARCHAR type_cd
        VARCHAR item_cd
        VARCHAR item_name_vn
        VARCHAR item_name_en
        INT sort_order
        BOOLEAN is_active
        BOOLEAN delete_flg
    }
```

---

## 2. Các Thực Thể Cơ Sở & Quy Ước Chung

### 2.1 Lớp Cơ Sở `BaseEntity`
Tất cả các thực thể nghiệp vụ kế thừa từ `BaseEntity` để tự động ghi nhận thông tin kiểm toán thông qua Spring Data JPA Auditing:
- `created_by` (VARCHAR): Tên người tạo bản ghi.
- `created_date` (TIMESTAMP WITH TIME ZONE): Thời điểm tạo bản ghi.
- `last_modified_by` (VARCHAR): Tên người sửa đổi cuối cùng.
- `last_modified_date` (TIMESTAMP WITH TIME ZONE): Thời điểm sửa đổi cuối cùng.

### 2.2 Chiến Lược Sinh Khóa Chính (Sequence Generator)
Dự án sử dụng sequence chung **`hibernate_sequence`** (bước nhảy `allocationSize = 50`) để tối ưu hóa hiệu năng chèn dữ liệu theo khối (Batch Inserts):
```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
Long id;
```

### 2.3 Cơ Chế Xóa Mềm (Soft Delete)
Các bảng nghiệp vụ đều có cột `delete_flg` (boolean). Khi thực hiện thao tác xóa, dữ liệu không bị xóa vĩnh viễn khỏi database mà chỉ được gán `delete_flg = true`. Mọi câu truy vấn Repository mặc định luôn có điều kiện `AND delete_flg = false`.

---

## 3. Chi Tiết Nhóm Bảng Nghiệp Vụ

### 3.1 Phân Hệ Người Dùng (Users & Profiles)
- **`user_logins`**: Quản lý thông tin đăng nhập tối thiểu (username, email, mật khẩu băm, trạng thái, khóa tài khoản). Tách rời khỏi hồ sơ để đảm bảo hiệu năng cao khi xác thực.
- **`user_infos`**: Chứa toàn bộ thông tin lý lịch cá nhân chi tiết (họ tên, CCCD, chức danh khoa học, ngạch viên chức...). Liên kết `1-1` với `user_logins`.

### 3.2 Phân Hệ Cây Tổ Chức (Org Tree & Closure Table)
- **`org_nodes`**: Cấu trúc hình cây đại diện cho Ban giám hiệu, các Phòng, Khoa, Trung tâm, Bộ môn. Tự tham chiếu `parent_id`.
- **`org_node_closures`**: Bảng quan hệ đường đi tổ tiên - hậu duệ (Ancestor - Descendant) kèm độ sâu (`depth`).
  - *Ưu điểm:* Cho phép lấy toàn bộ danh sách đơn vị cấp dưới của một Khoa/Phòng chỉ với 1 câu lệnh `SELECT ... WHERE ancestor_id = :nodeId` với tốc độ cực nhanh.

### 3.3 Phân Hệ Phân Quyền (RBAC Matrix)
- **`position_assignments`**: Gán người dùng vào vị trí/chức vụ cụ thể tại một `org_node` (có hiệu lực từ ngày đến ngày).
- **`position_permissions`**: Gán quyền nghiệp vụ (`permission_cd`) cho từng chức danh (`position_cd`). Nếu `inherit_to_children = true`, người nắm giữ chức danh này tại node cha sẽ có quyền quản lý xuống các node con.
- **`role_permissions`**: Gán quyền cấp hệ thống cho các vai trò toàn cục (`USER`, `ADMIN`, `SYSTEM_ADMIN`).
- **`position_degs`**: Lưu thông tin ủy quyền tạm thời giữa các vị trí.

### 3.4 Phân Hệ Danh Mục & Token
- **`dictionary_items`**: Bảng từ điển dữ liệu hệ thống (danh sách chức vụ `POSITION_CD`, loại cơ quan `ORG_NODE_TYPE_CD`, trạng thái hợp đồng...).
- **`refresh_tokens`**: Quản lý định danh `jti` (UUID) của refresh token, thời gian hết hạn và trạng thái thu hồi (`is_revoked`).

### 3.5 Phân Hệ Nhật Ký Kiểm Toán (Audit & Logs)
- **`user_audit_logs`**: Ghi nhận vết truy cập API (Client IP, User Agent, URI, Method, Tham số, Thời gian thực thi).
- **`change_history_logs`**: Ghi nhận biến động dữ liệu từng bảng (`table_name`, `object_id`, `action`), lưu trạng thái `before_data`, `after_data` và `changed_fields` dưới dạng **PostgreSQL JSONB**.

---

👉 **Đọc tiếp:** [03. Xác Thực & Bảo Mật JWT](./03_xac-thuc-va-bao-mat-jwt.md)

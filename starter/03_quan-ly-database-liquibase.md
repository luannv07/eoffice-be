# 🗄️ 03. Cơ Sở Dữ Liệu & Liquibase Migration

Tài liệu này giải thích cách quản lý cấu trúc bảng (schema) và dữ liệu khởi tạo (seed data) trong dự án **eoffice-be**
thông qua công cụ **Liquibase**.

---

## 1. Tại Sao Sử Dụng Liquibase?

Trong dự án `eoffice-be`:

- `spring.jpa.hibernate.ddl-auto` luôn được đặt là **`none`** (không để Hibernate tự động sinh hoặc sửa bảng để tránh
  mất mát dữ liệu sản xuất).
- Mọi thay đổi về bảng, cột, index, sequence, dữ liệu mẫu đều được định nghĩa bằng file YAML và kiểm soát phiên bản qua
  Git với **Liquibase**.
- **Tự động 100% khi khởi động:** Khi ứng dụng chạy, Liquibase tự động kết nối tới Database (dù là **AWS RDS Cloud** hay
  **Local PostgreSQL** được cấu hình trong `.env.staging`), so sánh trạng thái và tự động thực thi các thay đổi chưa
  chạy.
- **Lập trình viên không cần:** Mở tool pgAdmin/DBeaver để chạy script `.sql` thủ công khi cài đặt ban đầu.

---

## 2. Cấu Trúc Thư Mục Liquibase

```text
src/main/resources/db/changelog/
│
├── master.yml                                          <-- File tổng quản lý thứ tự chạy migration
└── changes/
    ├── 2026-05-30_add-audit-and-history-log-tables.yml <-- Tạo bảng audit log & change history log
    ├── 2026-05-30_optimize-audit-log-storage-and-indexes.yml <-- Đánh index tối ưu query audit
    ├── 2026-08-09_add-shared-sequence.yml              <-- Tạo hibernate_sequence dùng chung
    ├── 2026-08-09_add-auth-core-tables.yml             <-- Tạo bảng user_logins, user_infos, dictionary_items
    ├── 2026-08-09_add-rbac-org-tables.yml              <-- Tạo bảng org_nodes, closures, positions...
    ├── 2026-08-10_seed-system-users.yml               <-- Khởi tạo tài khoản hệ thống mẫu
    ├── 2026-08-10_seed-dictionary-and-permissions.yml  <-- Seed danh mục từ điển và quyền
    ├── 2026-08-10_fix-sequence-after-seed.yml          <-- Đồng bộ giá trị sequence sau khi seed
    └── 2026-08-12_add-refresh-tokens-table.yml        <-- Tạo bảng refresh_tokens
```

---

## 3. Các Bảng Đặc Biệt Của Liquibase

Khi chạy lần đầu, Liquibase sẽ tự động tạo 2 bảng nội bộ trong PostgreSQL:

- **`databasechangelog`**: Ghi lại lịch sử từng changeset đã chạy thành công (kèm checksum, thời gian thực thi).
- **`databasechangeloglock`**: Cơ chế khóa phân tán, đảm bảo khi có nhiều server chạy đồng thời thì chỉ có 1 instance
  thực hiện migrate DB.

---

## 4. Quy Trình Thêm Một Thay Đổi Bảng Mới (Từng Bước)

Giả sử bạn cần tạo thêm bảng mới `documents` (Quản lý văn bản):

### Bước 1: Tạo file changelog mới trong `src/main/resources/db/changelog/changes/`

Đặt tên file theo định dạng: `YYYY-MM-DD_mo-ta-ngan.yml` (ví dụ: `2026-08-24_create-documents-table.yml`).

Nội dung mẫu:

```yaml
databaseChangeLog:
  - changeSet:
      id: 2026-08-24-create-documents-table
      author: your_name
      changes:
        - createTable:
            tableName: documents
            columns:
              - column:
                  name: id
                  type: BIGINT
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: title
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: document_code
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: created_at
                  type: TIMESTAMP WITH TIME ZONE
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: delete_flg
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
```

### Bước 2: Khai báo file mới vào `master.yml`

Mở file `src/main/resources/db/changelog/master.yml` và thêm dòng mới vào cuối danh sách:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/2026-05-30_add-audit-and-history-log-tables.yml
  # ... các file cũ
  - include:
      file: db/changelog/changes/2026-08-24_create-documents-table.yml
```

### Bước 3: Khởi động lại ứng dụng

Chạy `.\mvnw.cmd spring-boot:run`. Liquibase sẽ đọc file mới và áp dụng vào cơ sở dữ liệu.

---

## 5. Nguyên Tắc Vàng Khi Làm Việc Với Liquibase

> [!CAUTION]
> 1. **KHÔNG BAO GIỜ sửa trực tiếp nội dung của một changeset đã được commit lên Git hoặc đã chạy trên máy của người
     khác.** (Nếu sửa, Liquibase sẽ báo lỗi sai `Checksum Validation Failed`).
> 2. Nếu cần sửa cấu trúc bảng đã tạo trước đó, hãy **tạo một file changeset mới** để sửa cột/thêm cột (ví dụ:
     `addColumn`, `modifyDataType`, `dropColumn`).
> 3. Luôn chỉ định kiểu dữ liệu tương thích với PostgreSQL (ví dụ: `TIMESTAMP WITH TIME ZONE`, `BIGINT`, `BOOLEAN`,
     `JSONB`).
> 4. Tất cả các bảng nghiệp vụ chính cần có sequence ID chung `hibernate_sequence` và cờ xóa mềm `delete_flg`.

---

👉 **Bước tiếp theo:** [04. Khắc Phục Sự Cố & FAQ](./04_khac-phuc-su-co-faq.md)

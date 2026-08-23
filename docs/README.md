# 📖 Tài Liệu Kỹ Thuật Dự Án `eoffice-be`

Chào mừng bạn đến với trung tâm tài liệu kỹ thuật của dự án **eoffice-be**. Tại đây, các chủ đề kiến trúc, bảo mật, cơ sở dữ liệu và quy chuẩn lập trình được phân chia thành từng đề mục riêng biệt, giúp các lập trình viên mới dễ dàng tiếp cận và làm chủ hệ thống.

---

## 🧭 Bản Đồ Tài Liệu (Documentation Map)

```
eoffice-be/
│
├── starter/                          <-- 🚀 BẮT ĐẦU TẠI ĐÂY: Hướng dẫn cài đặt & chạy dự án
│   ├── README.md                     <-- Mục lục khởi động nhanh
│   ├── 01_cai-dat-moi-truong.md       <-- Cài Java 21, Postgres, Docker
│   ├── 02_cau-hinh-va-khoi-chay.md    <-- Cấu hình .env.staging và chạy app
│   ├── 03_quan-ly-database-liquibase.md <-- Hướng dẫn Liquibase migration
│   └── 04_khac-phuc-su-co-faq.md      <-- Xử lý các lỗi thường gặp
│
└── docs/                             <-- 📚 TÀI LIỆU KIẾN TRÚC & PHÁT TRIỂN
    ├── README.md                     <-- (File này) Mục lục tài liệu kỹ thuật
    ├── 01_tong-quan-va-kien-truc.md   <-- Kiến trúc phân tầng & Tech Stack
    ├── 02_co-so-du-lieu-va-thuc-the.md <-- ERD, Thực thể JPA & Closure Table
    ├── 03_xac-thuc-va-bao-mat-jwt.md  <-- JWT Dual-token, Rotation, Rate Limit
    ├── 04_phan-quyen-rbac-va-to-chuc.md <-- Phân quyền theo cây tổ chức & vị trí
    ├── 05_he-thong-audit-va-lich-su.md <-- Nhật ký truy cập & biến động dữ liệu (AOP)
    └── 06_quy-chuan-lap-trinh-backend.md <-- Chuẩn RESTful, ErrorCode, BaseResponse
```

---

## 📑 Chi Tiết Các Chuyên Đề

| Chuyên đề | Nội dung chính | Đối tượng |
| :--- | :--- | :--- |
| [**01. Tổng Quan & Kiến Trúc**](./01_tong-quan-va-kien-truc.md) | Sơ đồ luồng ứng dụng, các tầng Controller - Service - Repository, Tech Stack | Tất cả lập trình viên |
| [**02. Cơ Sở Dữ Liệu & Thực Thể**](./02_co-so-du-lieu-va-thuc-the.md) | Chi tiết các bảng, quan hệ ForeignKey, giải pháp Closure Table và trường JSONB | Backend Dev, DBA |
| [**03. Xác Thực & Bảo Mật JWT**](./03_xac-thuc-va-bao-mat-jwt.md) | Cơ chế Access/Refresh token, chống Replay Attack, Rate Limiting, mã hóa BCrypt | Backend & Frontend Dev |
| [**04. Phân Quyền RBAC & Tổ Chức**](./04_phan-quyen-rbac-va-to-chuc.md) | Ma trận phân quyền 3 lớp: Role, Position tại Org Node, Ủy quyền | Backend Dev |
| [**05. Hệ Thống Audit & Lịch Sử**](./05_he-thong-audit-va-lich-su.md) | Cách sử dụng `@Auditable`, `@TrackChange`, xử lý Async Event không block luồng | Backend Dev |
| [**06. Quy Chuẩn Lập Trình Backend**](./06_quy-chuan-lap-trinh-backend.md) | Chuẩn viết API, BaseResponse, Custom Exception, viết Unit Test và DTO | Backend Dev |

---

> [!TIP]
> **Dành cho người mới bắt đầu:** Hãy đọc lần lượt từ thư mục [**`starter/`**](../starter/README.md) trước khi đi sâu vào các tài liệu chuyên đề trong thư mục này!

# eoffice-be

Backend Spring Boot cho hệ thống Văn phòng điện tử (e-Office) - Trường Đại học Điện Lực (Java 21 / Spring Boot 4 / PostgreSQL / JWT / RBAC).

---

## 🚀 Bắt Đầu Nhanh (Dành cho người mới)

Tất cả hướng dẫn cài đặt môi trường, cấu hình và chạy dự án từ A-Z được đặt trong thư mục **[`starter/`](./starter/README.md)**:

- 🛠️ [**01. Cài Đặt Môi Trường**](./starter/01_cai-dat-moi-truong.md): Java 21, PostgreSQL, Docker, IDE.
- ⚙️ [**02. Cấu Hình & Khởi Chạy**](./starter/02_cau-hinh-va-khoi-chay.md): Thiết lập `.env.staging`, chạy Maven Wrapper và Swagger UI.
- 🗄️ [**03. Cơ Sở Dữ Liệu & Liquibase**](./starter/03_quan-ly-database-liquibase.md): Cơ chế migration tự động.
- ❓ [**04. Khắc Phục Sự Cố (FAQ)**](./starter/04_khac-phuc-su-co-faq.md): Sửa các lỗi thường gặp khi chạy lần đầu.

---

## 📚 Tài Liệu Kỹ Thuật Chuyên Sâu

Xem toàn bộ tài liệu kiến trúc, bảo mật và phân quyền tại thư mục **[`docs/`](./docs/README.md)**:

1. 🏗️ [**Tổng Quan & Kiến Trúc Phân Tầng**](./docs/01_tong-quan-va-kien-truc.md)
2. 🗄️ [**Cơ Sở Dữ Liệu & Thực Thể JPA**](./docs/02_co-so-du-lieu-va-thuc-the.md)
3. 🔐 [**Xác Thực & Bảo Mật JWT (Dual-Token & Rotation)**](./docs/03_xac-thuc-va-bao-mat-jwt.md)
4. 🏢 [**Phân Quyền RBAC & Cây Tổ Chức (Closure Table)**](./docs/04_phan-quyen-rbac-va-to-chuc.md)
5. 📋 [**Hệ Thống Audit Log & Lịch Sử Biến Động (AOP)**](./docs/05_he-thong-audit-va-lich-su.md)
6. 📐 [**Quy Chuẩn Lập Trình Backend**](./docs/06_quy-chuan-lap-trinh-backend.md)

---

## ⚡ Lệnh Nhanh (Quick Commands)

```powershell
# Chạy app local (profile staging)
.\mvnw.cmd spring-boot:run

# Đóng gói sản phẩm (Build JAR)
.\mvnw.cmd -DskipTests package
```


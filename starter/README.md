# 🚀 Hướng Dẫn Khởi Động Cho Người Mới (Starter Kit)

Chào mừng bạn đến với dự án **eoffice-be**! Thư mục này chứa toàn bộ tài liệu hướng dẫn từng bước giúp bạn thiết lập môi
trường, cấu hình và chạy dự án một cách nhanh chóng nhất.

---

## 📚 Danh Mục Hướng Dẫn

|  STT   | Tài liệu                                                                | Mô tả                                                                    |
|:------:|:------------------------------------------------------------------------|:-------------------------------------------------------------------------|
| **01** | [**01. Cài Đặt Môi Trường**](./01_cai-dat-moi-truong.md)                | Cài đặt Java 21, PostgreSQL, Git, Docker và công cụ hỗ trợ.              |
| **02** | [**02. Cấu Hình & Khởi Chạy**](./02_cau-hinh-va-khoi-chay.md)           | Thiết lập file `.env.staging`, chạy ứng dụng và kiểm tra Swagger UI.     |
| **03** | [**03. Cơ Sở Dữ Liệu & Liquibase**](./03_quan-ly-database-liquibase.md) | Cách Liquibase tự động migrate bảng, dữ liệu mẫu và tạo thay đổi schema. |
| **04** | [**04. Khắc Phục Sự Cố (FAQ)**](./04_khac-phuc-su-co-faq.md)            | Tổng hợp các lỗi thường gặp khi cài đặt và cách xử lý nhanh.             |

---

## ⚡ Các Bước Chạy Nhanh Trong 3 Phút

### 🌟 Cách 1: Chạy ngay với Database Cloud AWS RDS (Không cần cài DB trên máy)

Dự án đã có sẵn file [`.env.staging`](file:///d:/Visual%20studio%20code/eoffice-be/.env.staging) kết nối tới AWS RDS
Staging:

```powershell
# Chỉ cần máy có Java 21 và kết nối Internet:
.\mvnw.cmd spring-boot:run
```

### 💻 Cách 2: Chạy với Database PostgreSQL Local / Docker

```powershell
# 1. Tạo file môi trường từ file mẫu
Copy-Item .env.staging.example .env.staging

# 2. Mở file .env.staging và điền thông tin kết nối DB của bạn
# DB_HOST=localhost, DB_PORT=5432, DB_NAME=eoffice, DB_USERNAME=..., DB_PASSWORD=...

# 3. Khởi chạy ứng dụng (hoặc chạy: docker compose up -d)
.\mvnw.cmd spring-boot:run
```

---

### 🎉 Kiểm tra ứng dụng sau khi chạy:

- **Swagger UI:** [http://localhost:8080/api/v1/swagger-ui.html](http://localhost:8080/api/v1/swagger-ui.html)
- **Health Check:** [http://localhost:8080/api/v1/public/health](http://localhost:8080/api/v1/public/health)

---

👉 **Bắt đầu ngay tại:** [01. Cài Đặt Môi Trường](./01_cai-dat-moi-truong.md)

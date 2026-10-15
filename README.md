# E-Office Backend (eoffice-be)

Hệ thống Quản lý Văn phòng Điện tử (e-Office), Quản lý Văn bản, Luồng phê duyệt động và Ký số điện tử PKI X.509 PAdES chuẩn Quốc gia dành cho Trường Đại học Điện Lực (EPU).

* **Công nghệ cốt lõi**: Java 21 LTS, Spring Boot 3.4.x, PostgreSQL 16, Apache PDFBox 3.x, BouncyCastle Security Provider, Hibernate/JPA, Liquibase, JWT (Dual-Token + Rotation), Docker.

---

## 📌 MỤC LỤC
1. [Các Tính Năng Đã Hoàn Thiện & Cải Tiến Mới Nhất](#1-các-tính-năng-đã-hoàn-thiện--cải-tiến-mới-nhất)
2. [Hạ Tầng Ký Số PKI & Kiểm Định Chữ Ký (Verify Engine)](#2-hạ-tầng-ký-số-pki--kiểm-định-chữ-ký-verify-engine)
3. [Hướng Dẫn Cài Đặt Khi Pull Code Về Máy Mới](#3-hướng-dẫn-cài-đặt-khi-pull-code-về-máy-mới)
4. [Tài Khoản Kiểm Thử Mẫu](#4-tài-khoản-kiểm-thử-mẫu)
5. [Quy Trình Nghiệp Vụ & Kiểm Thử API Từ A -> Z](#5-quy-trình-nghiệp-vụ--kiểm-thử-api-từ-a---z)
6. [Tài Liệu Kỹ Thuật Tham Khảo](#6-tài-liệu-kỹ-thuật-tham-khảo)

---

## 1. CÁC TÍNH NĂNG ĐÃ HOÀN THIỆN & CẢI TIẾN MỚI NHẤT

### 1.1. Ma trận chuyển đổi trạng thái luồng văn bản (Workflow State Machine)
Hệ thống chuẩn hóa luồng xử lý văn bản khép kín qua các trạng thái:
* `DRAFT` (Dự thảo) $\rightarrow$ `IN_REVIEW` (Đang thẩm định & ký nháy).
* `IN_REVIEW` $\rightarrow$ `PENDING_APPROVAL` (Thẩm định viên duyệt chuyển tiếp lên Lãnh đạo).
* `PENDING_APPROVAL` $\rightarrow$ `APPROVED` (Hiệu trưởng phê duyệt thông qua nội dung).
* `APPROVED` $\rightarrow$ `PUBLISHED` (Hiệu trưởng ký số phát hành chính thức, cấp số từ Sổ văn bản).
* **Cơ chế Trả về (`NEEDS_REVISION`) & Từ chối (`REJECTED`)**:
  * Tự động **đóng băng tệp PDF cũ** (`primaryFilePath = null`, hủy cờ `isMainDocument`).
  * Bắt buộc người soạn phải tải lên tệp sửa đổi mới (v2) trước khi gửi duyệt lại (`DOCUMENT_INCOMPLETE_DOSSIER`).
  * Cho phép người soạn rút văn bản về hẳn `DRAFT` (`recall-approval`).

### 1.2. Cấp số tự động qua Sổ văn bản (Pessimistic Write Lock)
* Khóa dòng ghi sổ (`SELECT ... FOR UPDATE`) chống tuyệt đối hiện tượng trùng số văn bản khi có nhiều yêu cầu ban hành đồng thời.
* Định dạng số linh hoạt theo mẫu sổ (`{NUM}/KH-ĐHEPU`, `{NUM}/QĐ-ĐHEPU`).

### 1.3. Cơ chế xử lý lỗi toàn cục (Global Exception Handling)
* Tự động bắt và chuyển đổi các lỗi Jackson/DTO (như gửi nhầm trường, thiếu Request Body) thành mã lỗi nghiệp vụ `400 BAD_REQUEST` rõ ràng thay vì lỗi hệ thống `500`.
* Bắt chặn lỗi cố tình ký đè nhiều lần trên cùng một tài liệu PDF.

---

## 2. HẠ TẦNG KÝ SỐ PKI & KIỂM ĐỊNH CHỮ KÝ (VERIFY ENGINE)

### 2.1. Ký số điện tử PKI X.509 PAdES (Digital Signature)
* **Root CA nội bộ**: Quản lý chứng thư số Root CA (`uploads/keystores/ca/ca.p12`) với công cụ khởi tạo chuyên dụng, an toàn chống ghi đè (`scripts/init-ca.ps1`).
* **Cấp phát chứng thư người dùng tự động**: Khi người dùng ký lần đầu, hệ thống tự động sinh cặp khóa RSA 2048-bit và ký cấp chứng thư X.509 gắn liền với chức vụ và đơn vị công tác (`uploads/keystores/users/user_<id>.p12`).
* **Lưu tăng dần (Incremental Save)**: Đảm bảo văn bản qua nhiều lần ký nháy (Chuyên viên $\rightarrow$ Thẩm định viên $\rightarrow$ Hiệu trưởng) không bị hỏng chữ ký của người ký trước.
* **Niêm phong DocMDP ($P=1$)**: Khi ban hành, tài liệu được khóa niêm phong chống mọi hành vi chỉnh sửa sau ký.

### 2.2. Động cơ kiểm định chữ ký số (7 Tiêu chí bảo mật)
Hệ thống tích hợp sẵn endpoint kiểm định độc lập `GET /api/v1/documents/{id}/signatures/verify` để kiểm tra toàn diện 7 tiêu chí pháp lý:
1. **INTEGRITY**: Tính toán SHA-256 trên toàn bộ ByteRange, so khớp với `messageDigest` trong CMS signed attributes.
2. **CRYPTOGRAPHIC VALIDITY**: Xác minh chữ ký mật mã CMS Detached bằng PublicKey trong chứng thư.
3. **VALIDITY PERIOD**: Kiểm tra thời hạn hiệu lực của chứng thư số tại thời điểm ký và hiện tại.
4. **CA TRUST CHAIN**: Truy vết chuỗi tin cậy ngược về Root CA nội bộ EPU.
5. **REVOCATION STATUS**: Kiểm tra trạng thái thu hồi chứng thư (CRL/Revocation List).
6. **BYTERANGE COVERAGE**: Đảm bảo vùng ký bao phủ toàn bộ tệp PDF, phát hiện dữ liệu chèn lén sau chữ ký.
7. **DOCMDP / MODIFICATION DETECTION**: Phát hiện bất kỳ thay đổi nào làm phá vỡ niêm phong của tài liệu.

---

## 3. HƯỚNG DẪN CÀI ĐẶT KHI PULL CODE VỀ MÁY MỚI

### Bước 1: Yêu cầu môi trường
* **JDK**: Java 21 (Oracle OpenJDK hoặc Eclipse Temurin 21).
* **Database**: PostgreSQL 16 (chạy trực tiếp hoặc qua Docker).
* **Git** & **Maven** (hoặc dùng `mvnw` đi kèm dự án).

### Bước 2: Khởi động cơ sở dữ liệu PostgreSQL (Docker)
Nếu máy bạn đã cài Docker, chỉ cần chạy lệnh sau để dựng container Postgres:
```bash
docker run -d \
  --name eoffice-postgres \
  -e POSTGRES_DB=eoffice \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

### Bước 3: Cấu hình file môi trường (`.env.staging`)
Tạo hoặc kiểm tra file `.env.staging` tại thư mục gốc của backend (`eoffice-be/.env.staging`):
```properties
SPRING_PROFILES_ACTIVE=staging

# Kết nối PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_NAME=eoffice
DB_USERNAME=postgres
DB_PASSWORD=postgres

TZ=UTC
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC
SPRING_JACKSON_TIME_ZONE=UTC
SPRING_JPA_PROPERTIES_HIBERNATE_JDBC_TIME_ZONE=UTC

# JWT Secret (Tối thiểu 32 ký tự)
JWT_SECRET=eoffice-be-jwt-secret-fixed-for-all-environments-2026

COOKIE_PATH=/api/v1/auth
COOKIE_SECURE=false
JWT_ALLOWED_ORIGINS=
```

### Bước 4: Khởi tạo Root CA nội bộ (Chỉ cần chạy 1 lần cho máy mới clone code)
Do thư mục `uploads/keystores/` chứa khóa mật mã được loại trừ khỏi Git theo quy chuẩn an toàn, bạn cần khởi tạo Root CA để phục vụ tính năng ký số:
```powershell
# Trên Windows (PowerShell):
.\scripts\init-ca.ps1

# Trên Linux / macOS / Docker:
chmod +x ./scripts/init-ca.sh
./scripts/init-ca.sh
```
> [!NOTE]
> - Script đã tích hợp **chốt chặn an toàn chống ghi đè 100%**: Nếu file `ca.p12` đã tồn tại hoặc đã có chứng thư user, script sẽ lập tức từ chối và thoát ngay để bảo vệ các chữ ký cũ.
> - Mật khẩu Keystore được đặt cố định là `changeit` đồng bộ hoàn toàn với backend Java.

### Bước 5: Chạy kiểm thử tự động (Auto Liquibase Migration)
Chạy lệnh sau để Maven tự động tải thư viện, kích hoạt Liquibase tạo bảng và chạy toàn bộ test case:
```powershell
# Chạy toàn bộ test
.\mvnw.cmd test

# Hoặc chạy kiểm tra nhanh
.\mvnw.cmd test -Dtest=AuthCoreContextTest
```

### Bước 6: Khởi chạy Backend Application
```powershell
.\mvnw.cmd spring-boot:run
```
Sau khi khởi động thành công:
* **API Context Path**: `http://localhost:8080/api/v1`
* **Swagger UI / OpenAPI Documentation**: `http://localhost:8080/api/v1/swagger-ui.html`

---

## 4. TÀI KHOẢN KIỂM THỬ MẪU

| Username | Role hệ thống | Chức danh / Đơn vị | Mục đích kiểm thử |
| :--- | :--- | :--- | :--- |
| **`systemadmin`** | `SYSTEM_ADMIN` | Quản trị hệ thống | Quản trị toàn quyền, tạo sổ văn bản, điều phối luồng |
| **`cb.thamdinh`** | `REVIEWER` | Trưởng phòng (`TP`) - EPU | Thẩm định văn bản, ký nháy lần 2, duyệt chuyển tiếp (`approve`), trả về (`request-revision`), từ chối (`reject`) |
| **`ht.nguyen`** | `LEADER` | Hiệu trưởng (`HT`) - EPU | Lãnh đạo phê duyệt (`approve`), Ký số PKI và phát hành chính thức (`sign-and-publish`) |

---

## 5. QUY TRÌNH NGHIỆP VỤ & KIỂM THỬ API TỪ A -> Z

### Bước 1: Đăng nhập lấy Bearer Token
* **POST** `http://localhost:8080/api/v1/auth/login`
* **Body**:
  ```json
  {
    "username": "ht.nguyen",
    "password": "<mat_khau_cua_ban>"
  }
  ```
* Lấy chuỗi `data.accessToken` gắn vào Header các request sau: `Authorization: Bearer <TOKEN>`.

### Bước 2: Tạo dự thảo văn bản (`DRAFT`)
* **POST** `http://localhost:8080/api/v1/documents/drafts`
* **Body**:
  ```json
  {
    "title": "Kế hoạch tổ chức Hội nghị Khoa học năm 2026",
    "summary": "Kế hoạch triển khai hội thảo chuyên đề nghiên cứu khoa học",
    "content": "Căn cứ kế hoạch công tác năm học 2026...",
    "documentTypeCd": "KE_HOACH",
    "issuingOrgNodeId": 5001,
    "securityLevel": "NORMAL",
    "urgencyLevel": "NORMAL"
  }
  ```

### Bước 3: Tải tệp PDF chính lên dự thảo
* **POST** `http://localhost:8080/api/v1/documents/{id}/attachments`
* **Body** (`multipart/form-data`):
  * `file`: Chọn file PDF từ máy tính
  * `isMain`: `true`

### Bước 4: Chuyên viên gửi thẩm định (`DRAFT` $\rightarrow$ `IN_REVIEW`)
* **POST** `http://localhost:8080/api/v1/documents/{id}/submit-approval`
* **Body**:
  ```json
  {
    "approverUserId": 11753,
    "submitNote": "Kính gửi Thẩm định viên xem xét duyệt bản dự thảo"
  }
  ```

### Bước 5: Cán bộ thẩm định duyệt chuyển tiếp (`IN_REVIEW` $\rightarrow$ `PENDING_APPROVAL`)
* **POST** `http://localhost:8080/api/v1/documents/{id}/workflow/approve`
* **Headers**: `Authorization: Bearer <TOKEN_CỦA_CB_THAMDINH>`
* **Body**:
  ```json
  {
    "comment": "Đã thẩm định xong nội dung, kính chuyển Hiệu trưởng phê duyệt"
  }
  ```

### Bước 6: Hiệu trưởng phê duyệt nội dung (`PENDING_APPROVAL` $\rightarrow$ `APPROVED`)
* **POST** `http://localhost:8080/api/v1/documents/{id}/workflow/approve`
* **Headers**: `Authorization: Bearer <TOKEN_CỦA_HT_NGUYEN>`
* **Body**:
  ```json
  {
    "comment": "Hiệu trưởng đồng ý phê duyệt kế hoạch"
  }
  ```

### Bước 7: Hiệu trưởng Ký số và Ban hành (`APPROVED` $\rightarrow$ `PUBLISHED`)
* **POST** `http://localhost:8080/api/v1/documents/{id}/workflow/sign-and-publish`
* **Headers**: `Authorization: Bearer <TOKEN_CỦA_HT_NGUYEN>`
* **Body**:
  ```json
  {
    "bookId": 6006,
    "comment": "Ký duyệt và ban hành văn bản chính thức"
  }
  ```
  *(Sổ Kế hoạch: `bookId = 6006`, Sổ Quyết định: `bookId = 6001`)*.

### Bước 8: Kiểm định chữ ký số trên tài liệu vừa ban hành
* **GET** `http://localhost:8080/api/v1/documents/{id}/signatures/verify`
* **Headers**: `Authorization: Bearer <TOKEN>`
* *Trả về chi tiết 7 tiêu chí bảo mật, danh tính người ký, chuỗi CA và đánh giá `overallStatus: VALID`.*

### Bước 9: Tải tệp PDF chính thức đã ký số
* **GET** `http://localhost:8080/api/v1/documents/{id}/download`
* **Headers**: `Authorization: Bearer <TOKEN>`

---

## 6. TÀI LIỆU KỸ THUẬT THAM KHẢO

Xem chi tiết trong thư mục [`docs/`](./docs/README.md) và [`starter/`](./starter/README.md):
* [01. Cài đặt môi trường](./starter/01_cai-dat-moi-truong.md)
* [02. Cấu hình & Khởi chạy](./starter/02_cau-hinh-va-khoi-chay.md)
* [03. Quản lý Cơ sở dữ liệu Liquibase](./starter/03_quan-ly-database-liquibase.md)
* [Tài liệu Kiến trúc & Phân quyền](./docs/01_tong-quan-va-kien-truc.md)

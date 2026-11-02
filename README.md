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
* **Root CA nội bộ**: Quản lý chứng thư số Root CA bằng API dành cho `SYSTEM_ADMIN`, lưu keystore mã hóa trong database, tạo khóa RSA 3072-bit và chứng thư CA hiệu lực 3650 ngày; từ chối tạo đè CA đang có, kể cả đã hết hạn.
* **Cấp phát chứng thư người dùng tự động**: Khi người dùng ký lần đầu, hệ thống tự động sinh cặp khóa RSA 2048-bit và cấp chứng thư X.509 (`uploads/keystores/users/user_<id>.p12`). Hiệu lực tối đa 3 năm, không vượt quá hạn CA. Đăng ký và hoàn tất profile chưa cấp key. Lần ký sau dùng lại key; key thiếu sau khi đã sử dụng hoặc key hỏng/hết hạn yêu cầu khôi phục, không tự tạo thay thế.
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

### 2.3. API quản trị Root CA

Hai API dưới đây yêu cầu `Authorization: Bearer <SYSTEM_ADMIN_ACCESS_TOKEN>`. Chỉ `SYSTEM_ADMIN` được gọi; hai endpoint này không yêu cầu hoàn tất profile để quản trị viên có thể khởi tạo CA. API không trả private key hoặc mật khẩu.

| API | Kết quả |
| :--- | :--- |
| `GET /api/v1/admin/pki/ca/status` | HTTP 200, `BaseResponse.data` gồm `state`, `canInitialize`, `subject`, `fingerprintSha256`, `notBefore`, `notAfter`; trường chứng thư có thể trống nếu chưa đọc được. Không tạo hoặc sửa file. |
| `POST /api/v1/admin/pki/ca/initialize` | Không gửi body. HTTP 201, trả trạng thái CA vừa tạo. Chỉ khởi tạo khi chưa có CA và không còn dấu vết bộ key/chữ ký cũ. |

`state` nhận một trong bốn giá trị: `NOT_INITIALIZED`, `READY`, `EXPIRED`, `RECOVERY_REQUIRED`. Lỗi truy cập nơi lưu trả HTTP 503. CA chỉ được tạo một lần, không tự khởi tạo khi ứng dụng restart và chưa có API gia hạn hoặc luân chuyển CA.

| Mã lỗi | HTTP | Cách xử lý |
| :--- | :--- | :--- |
| `PKI_CA_NOT_INITIALIZED` | 503 | Chưa có CA khi ký: quản trị viên kiểm tra `status` rồi khởi tạo lần đầu. |
| `PKI_CA_ALREADY_EXISTS` | 409 | CA đã có, kể cả hết hạn: giữ nguyên bộ key, không gọi lại để tạo đè. |
| `PKI_CA_RECOVERY_REQUIRED` | 409 | CA thiếu/hỏng/không khớp hoặc còn lịch sử ký: khôi phục đúng bộ backup. |
| `PKI_CA_EXPIRED` | 503 | Ngừng ký và cấp chứng thư; xử lý gia hạn riêng, không xóa CA để khởi tạo lại. |
| `PKI_STORAGE_ERROR` | 503 | Kiểm tra đường dẫn và quyền truy cập của tiến trình backend. |
| `PKI_USER_KEYSTORE_INVALID` | 409 | Khôi phục/kiểm tra đúng keystore user; hệ thống không tự thay key cũ. |

Các lỗi PKI giữ nguyên mã khi đi qua luồng gửi duyệt, cấp số và phát hành; lỗi xử lý chữ ký khác dùng `SIGNATURE_PROCESS_ERROR`.

### 2.4. Lưu file, backup và khôi phục cho demo

PKI tiếp tục lưu bằng file, không có bảng/migration lưu CA hoặc chứng thư trong database. Đường dẫn lấy từ `PKI_CA_KEYSTORE_PATH`, `PKI_CA_CERT_PATH`, `PKI_USER_KEYSTORES_DIR`, `PKI_REVOKED_PATH`; mật khẩu keystore lấy từ `DEMO_KS_PASSWORD`. Các thư mục này độc lập với provider lưu tài liệu Telegram.

1. Trước deploy và sau khi cấp thêm chứng thư/thu hồi, tạm ngừng thao tác ký rồi sao lưu **cùng một bộ**: `ca.p12`, `ca.crt`, toàn bộ thư mục keystore user và `revoked.json`. Giữ nguyên cấu trúc đường dẫn; bảo vệ bản sao vì có private key.
2. Lưu mật khẩu keystore riêng khỏi gói backup. Khôi phục phải dùng đúng mật khẩu ban đầu, không đổi `DEMO_KS_PASSWORD` tùy tiện.
3. Sau deploy/restart, khôi phục toàn bộ bộ file vào các đường dẫn đã cấu hình trên **instance web thực sự nhận request ký**, kiểm tra quyền đọc/ghi, gọi API `status` và đối chiếu fingerprint CA với bản backup. Chỉ mở lại thao tác ký khi `READY`.
4. Chỉ gọi `initialize` khi chưa từng có CA và `canInitialize=true`. Nếu cần khôi phục thì giữ nguyên dữ liệu còn lại, không xóa dấu vết key/lịch sử ký để vượt chốt chặn.

**Giới hạn Heroku của bản demo:** filesystem dyno là tạm thời; deploy/restart có thể làm mất file. One-off dyno có filesystem riêng, nên chạy script hoặc chép backup vào one-off dyno không cấp key cho web dyno. Gọi API trên web dyno tạo key tại chính instance đó; nhiều web dyno giữ file riêng cũng không chia sẻ key. Bản demo dùng một web instance và backup/khôi phục thủ công, chưa bổ sung lưu trữ bền vững. Tham khảo [filesystem Heroku](https://devcenter.heroku.com/articles/dynos#ephemeral-filesystem).

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

### Bước 4: Cấu hình PKI cho môi trường đang chạy
CA được khởi tạo bằng API và keystore được lưu mã hóa trong database. Cấu hình các biến sau trong môi trường của backend; đặt mật khẩu riêng và khóa mã hóa trước lần khởi tạo đầu tiên. Các đường dẫn được giữ để kiểm tra dấu vết PKI cũ; không xóa dữ liệu đang có để ép khởi tạo CA mới:
```properties
PKI_CA_KEYSTORE_PATH=./uploads/keystores/ca/ca.p12
PKI_CA_CERT_PATH=./uploads/keystores/ca/ca.crt
PKI_USER_KEYSTORES_DIR=./uploads/keystores/users
PKI_REVOKED_PATH=./uploads/keystores/ca/revoked.json
DEMO_KS_PASSWORD=<mat_khau_keystore_rieng>
PKI_ENCRYPTION_KEY=<base64_cua_32_byte_ngau_nhien>
PKI_ENCRYPTION_KEY_ID=pki-key-01
```
Giữ bí mật và sao lưu khóa mã hóa ngoài Git. Sau khi chạy backend ở bước 6, đăng nhập tài khoản `SYSTEM_ADMIN`, gọi `GET /api/v1/admin/pki/ca/status`, sau đó gọi `POST /api/v1/admin/pki/ca/initialize` không body nếu `canInitialize=true`. Khi trạng thái là `READY`, dùng CA hiện có; nếu là `EXPIRED` hoặc `RECOVERY_REQUIRED`, xử lý khôi phục trước khi ký, không tạo thay CA.

### Bước 5: Chạy kiểm thử tự động (Auto Liquibase Migration)
Chạy lệnh sau để Maven tự động tải thư viện, kích hoạt Liquibase tạo bảng và chạy toàn bộ test case:
Các test PKI tự chuẩn bị CA/khóa riêng qua fixture; không cần khởi tạo CA của ứng dụng trước khi chạy test local hoặc CI.
```powershell
# Chạy toàn bộ test
.\mvnw.cmd test -Dspring.profiles.active=test

# Hoặc chạy kiểm tra nhanh
.\mvnw.cmd test -Dspring.profiles.active=test -Dtest=AuthCoreContextTest
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

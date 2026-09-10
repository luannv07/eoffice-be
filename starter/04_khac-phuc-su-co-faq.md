# ❓ 04. Khắc Phục Sự Cố & Câu Hỏi Thường Gặp (FAQ)

Tổng hợp các lỗi phổ biến khi cài đặt hoặc chạy dự án **eoffice-be** lần đầu và cách giải quyết nhanh nhất.

---

## 1. Lỗi Kết Nối Cơ Sở Dữ Liệu (`Connection Refused` / `SocketTimeout` / `Authentication failed`)

### 🔴 Triệu chứng:

Ứng dụng dừng ngay khi khởi động với thông báo lỗi:

- `Connection to ...rds.amazonaws.com:5432 refused` hoặc `The connection attempt failed` (khi dùng AWS RDS).
- `org.postgresql.util.PSQLException: Connection to localhost:5432 refused` (khi dùng Localhost).
- `FATAL: password authentication failed for user...` (sai tài khoản hoặc mật khẩu).

### 🟢 Cách xử lý:

#### A. Nếu đang dùng Database AWS RDS:

1. **Kiểm tra kết nối Internet:** AWS RDS là cơ sở dữ liệu online nên máy tính bắt buộc phải có Internet.
2. **Kiểm tra tường lửa mạng (Firewall / Mạng công ty):** Một số mạng nội bộ hoặc WiFi công cộng chặn cổng ra `5432`.
   Hãy thử chuyển sang mạng 4G/WiFi khác hoặc chuyển sang dùng **PostgreSQL Localhost** (xem mục B).
3. **Kiểm tra thông tin trong file [`.env.staging`](file:///d:/Visual%20studio%20code/eoffice-be/.env.staging):** Đảm
   bảo không bị sửa nhầm giá trị của các biến `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

#### B. Nếu chuyển sang dùng Database Local trên máy:

1. **Kiểm tra PostgreSQL service đã chạy chưa:**
    - Trên Windows: Mở Task Manager -> Services -> tìm `postgresql-x64-16` -> bấm Start.
    - Nếu dùng Docker: Chạy `docker ps` để kiểm tra container postgres đang chạy. Nếu chưa, chạy
      `docker start eoffice-postgres`.
2. **Kiểm tra thông tin trong file `.env.staging`:**
    - Đảm bảo `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=eoffice`, `DB_USERNAME=postgres` và `DB_PASSWORD` hoàn toàn
      khớp với mật khẩu tài khoản DB trên máy bạn.
3. **Database đã được tạo chưa?**
    - Đảm bảo bạn đã tạo database có tên là `eoffice` (hoặc tên được khai báo ở `DB_NAME`).

---

## 2. Lỗi Trùng Port (`Port 8080 is already in use`)

### 🔴 Triệu chứng:

`Web server failed to start. Port 8080 was already in use.`

### 🟢 Cách xử lý:

- **Cách 1 (Đổi Port dự án):** Mở file `.env.staging`, đổi `PORT=8081` hoặc port bất kỳ còn trống.
- **Cách 2 (Tắt tiến trình đang chiếm Port 8080 trên Windows):**
  ```powershell
  # Tìm PID tiến trình đang chiếm port 8080
  netstat -ano | findstr :8080
  
  # Giết tiến trình theo PID (ví dụ PID là 12345)
  taskkill /F /PID 12345
  ```

---

## 3. Lỗi Khóa Bảng Liquibase (`Waiting for changelog lock`)

### 🔴 Triệu chứng:

Ứng dụng bị treo ở bước khởi động với log:
`Waiting for changelog lock....`

### 🟢 Nguyên nhân & Cách xử lý:

Hiện tượng này xảy ra khi ở lần chạy trước bạn tắt ứng dụng đột ngột giữa lúc Liquibase đang thực hiện migration, khiến
cờ lock vẫn bị bật trong bảng `databasechangeloglock`.

- Mở pgAdmin hoặc `psql` kết nối vào database `eoffice` và chạy lệnh giải phóng lock:
  ```sql
  UPDATE databasechangeloglock SET locked = false, lockgranted = null, lockedby = null WHERE id = 1;
  ```
- Khởi động lại ứng dụng.

---

## 4. Lỗi Sai Checksum Liquibase (`ValidationFailedException: Checksum changed`)

### 🔴 Triệu chứng:

`Validation Failed: 1 change sets check sum ... was: ... but is now: ...`

### 🟢 Cách xử lý:

- Nguyên nhân: Bạn đã chỉnh sửa nội dung của một file changeset cũ đã từng chạy trước đó.
- **Giải pháp đúng chuẩn:** Khôi phục lại nội dung file cũ về nguyên bản. Nếu muốn sửa đổi cấu trúc cột/bảng, hãy viết
  một file changeset mới.
- **Nếu đang ở môi trường dev cá nhân:** Bạn có thể xóa sạch database và chạy lại từ đầu:
  ```sql
  DROP DATABASE eoffice;
  CREATE DATABASE eoffice;
  ```

---

## 5. Lỗi Thiếu JWT Secret Key

### 🔴 Triệu chứng:

Ứng dụng báo lỗi khởi động liên quan đến `JwtProperties` hoặc `secret-key`.

### 🟢 Cách xử lý:

- Thuật toán HS256 yêu cầu khóa bí mật phải dài tối thiểu **32 ký tự (256 bits)**.
- Mở file `.env.staging` và đảm bảo biến `JWT_SECRET` có giá trị dài và hợp lệ, ví dụ:
  `JWT_SECRET=eoffice-be-jwt-secret-fixed-for-all-environments-2026`

---

## 6. Lỗi Biên Dịch Lombok Trong IDE (Không tìm thấy Getter / Setter / Builder)

### 🔴 Triệu chứng:

IDE báo lỗi đỏ ở các phương thức `.getId()`, `.builder()` dù file code không sai cú pháp.

### 🟢 Cách xử lý:

- Trong **IntelliJ IDEA**:
    1. Vào **Settings** -> **Build, Execution, Deployment** -> **Compiler** -> **Annotation Processors**.
    2. Đánh dấu tích vào **Enable annotation processing** -> Bấm **Apply** -> **OK**.
    3. Chọn menu **Build** -> **Rebuild Project**.

---

## 7. Lỗi Chặn CORS Từ Giao Diện Frontend (React / Vue / Next.js)

### 🔴 Triệu chứng:

Frontend gọi API bị lỗi `Cross-Origin Request Blocked`.

### 🟢 Cách xử lý:

- Mở file `.env.staging`, thêm địa chỉ domain của frontend vào biến `JWT_ALLOWED_ORIGINS` (ngăn cách bởi dấu phẩy):
  ```properties
  JWT_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080
  ```
- Khởi động lại ứng dụng backend.

---

🎉 **Bạn đã nắm trọn bộ hướng dẫn khởi động! Tiếp tục khám phá tài liệu kiến trúc sâu hơn tại thư
mục [docs/](../docs/README.md).**

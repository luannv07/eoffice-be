# 🛠️ 01. Cài Đặt Môi Trường Phát Triển

Tài liệu này hướng dẫn bạn cài đặt các công cụ và phần mềm cần thiết trước khi chạy dự án **eoffice-be**.

---

## 1. Yêu Cầu Phần Cứng & Phần Mềm

| Phần mềm                       | Phiên bản yêu cầu            | Mục đích                                                  |
|:-------------------------------|:-----------------------------|:----------------------------------------------------------|
| **JDK (Java Development Kit)** | **Java 21 LTS** *(Bắt buộc)* | Nền tảng thực thi ứng dụng Spring Boot 4                  |
| **Cơ sở dữ liệu**              | **PostgreSQL 15+**           | Lưu trữ dữ liệu chính *(Hoặc dùng sẵn AWS RDS dựng sẵn)*  |
| **Build Tool**                 | **Maven Wrapper (có sẵn)**   | Đóng gói và quản lý thư viện (không cần cài riêng)        |
| **Quản lý mã nguồn**           | **Git**                      | Clone và cập nhật source code                             |
| **IDE khuyên dùng**            | **IntelliJ IDEA / VS Code**  | Môi trường lập trình                                      |
| **Docker** *(Tùy chọn)*        | **Docker Desktop**           | Dùng nếu muốn chạy Backend hoặc PostgreSQL bằng container |

---

## 2. Hướng Dẫn Cài Đặt Từng Thành Phần

### 2.1 Cài Đặt Java 21 LTS

Dự án sử dụng **Java 21**. Khuyên dùng bản phân phối **Eclipse Temurin (Adoptium)** hoặc **Amazon Corretto 21**.

- **Tải bộ cài:** [https://adoptium.net/temurin/releases/?version=21](https://adoptium.net/temurin/releases/?version=21)
- **Kiểm tra sau khi cài đặt (mở Terminal / PowerShell):**
  ```powershell
  java -version
  ```
  *Kết quả hợp lệ phải hiển thị `openjdk version "21.x.x"`.*

> [!IMPORTANT]
> Hãy chắc chắn biến môi trường `JAVA_HOME` đã được trỏ tới thư mục cài đặt JDK 21 và đường dẫn `%JAVA_HOME%\bin` (
> Windows) đã có trong biến `PATH`.

---

### 2.2 Chuẩn Bị Cơ Sở Dữ Liệu PostgreSQL

Bạn có thể chọn **1 trong 3 cách** sau tùy theo nhu cầu:

#### 👉 Cách A: Dùng sẵn Database Cloud AWS RDS (Khuyên dùng - Nhanh nhất)

Dự án đã dựng sẵn một máy chủ **PostgreSQL trên AWS RDS** cho môi trường Staging/Test:

* **Ưu điểm:** Không cần cài đặt bất kỳ phần mềm Database nào trên máy tính của bạn.
* **Yêu cầu:** Máy tính chỉ cần có kết nối Internet.
* Thông tin kết nối đã được cấu hình sẵn trong file [
  `.env.staging`](file:///d:/Visual%20studio%20code/eoffice-be/.env.staging). Khi bạn bật backend, Liquibase sẽ tự động
  kết nối và tạo bảng trên AWS.

#### Cách B: Chạy PostgreSQL nhanh bằng Docker (Nếu muốn làm việc Offline)

Nếu máy bạn đã cài sẵn Docker và muốn chạy DB độc lập trên máy:

```powershell
# 1. Tạo network chung
docker network create eoffice-net

# 2. Khởi chạy container PostgreSQL
docker run --name eoffice-postgres --network eoffice-net -e POSTGRES_DB=eoffice -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16-alpine
```

#### Cách C: Cài đặt PostgreSQL trực tiếp trên máy (Native)

1. Tải bộ cài PostgreSQL 16: [https://www.postgresql.org/download/](https://www.postgresql.org/download/)
2. Chạy file cài đặt, đặt mật khẩu cho user `postgres` (port `5432`).
3. Mở pgAdmin hoặc `psql` tạo database:
   ```sql
   CREATE DATABASE eoffice;
   ```

> [!NOTE]
> Dự án đã có sẵn file **`Dockerfile`** tại thư mục gốc để đóng gói toàn bộ mã nguồn `eoffice-be` (Java 21). Xem chi
> tiết cách build và chạy container ứng dụng tại [Tài liệu 02: Cấu Hình & Khởi Chạy](./02_cau-hinh-va-khoi-chay.md).


---

### 2.3 Cấu Hình IDE (Môi Trường Lập Trình)

#### Với IntelliJ IDEA (Khuyên dùng):

1. Mở thư mục dự án `eoffice-be`.
2. Vào **File** -> **Project Structure** -> **Project**:
    - Chọn **SDK: 21**.
    - Chọn **Language level: 21 - Virtual threads, etc.**
3. Cài đặt plugin **Lombok** (nếu dùng bản Community cũ; các bản mới đã tích hợp sẵn).
4. Bật Annotation Processing: **Settings** -> **Build, Execution, Deployment** -> **Compiler** -> **Annotation
   Processors** -> Tích chọn **Enable annotation processing**.

#### Với Visual Studio Code:

1. Cài đặt extension pack: **Extension Pack for Java** (Microsoft) và **Spring Boot Extension Pack** (VMware).
2. Kiểm tra Java runtime đã trỏ về JDK 21.

---

## 3. Kiểm Tra Toàn Bộ Môi Trường

Chạy lệnh sau tại thư mục gốc của dự án để kiểm tra Maven wrapper và mã nguồn:

```powershell
# Trên Windows
.\mvnw.cmd -v

# Trên Linux / macOS
./mvnw -v
```

Nếu hiển thị phiên bản Apache Maven và Java version 21, môi trường của bạn đã sẵn sàng!

---

👉 **Bước tiếp theo:** [02. Cấu Hình & Khởi Chạy Ứng Dụng](./02_cau-hinh-va-khoi-chay.md)

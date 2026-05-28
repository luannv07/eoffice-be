# eoffice-be

Hướng dẫn chạy project (Tiếng Việt)

## Tổng quan
Project là ứng dụng backend Spring Boot (Java) cho eOffice.

## Yêu cầu
- Java 21 (OpenJDK 21)
- Maven (không bắt buộc vì repository có `mvnw`)
- Docker & Docker Compose (nếu muốn chạy database bằng Docker)
- Cổng mặc định: 8080

> Lưu ý: project chứa `mvnw` (Maven Wrapper) nên bạn có thể dùng `./mvnw` mà không cần cài Maven hệ thống.

## Cấu hình mặc định
Các cấu hình môi trường nằm trong `src/main/resources/`:
- `application.yaml` (chung)
- `application-local.yml` (cấu hình local, mặc định kết nối PostgreSQL)

Nội dung `application-local.yml` mặc định (DB):
```
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/eoffice
    username: eoffice
    password: luannv@8ck
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false

server:
  port: 8080
```

Nếu bạn muốn thay đổi cấu hình DB, chỉnh trực tiếp file trên hoặc dùng biến môi trường chuẩn Spring Boot (ví dụ `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`).

## Khởi tạo database (2 cách)

Cách A — Dùng Docker Compose (khuyến nghị cho local):

```bash
# chạy Postgres theo file docker-compose.local.yml
docker compose -f docker-compose.local.yml up -d --build
```
Postgres sẽ chạy trên `localhost:5432` với database/user/password trong file:
- DB: `eoffice`
- USER: `eoffice`
- PASSWORD: `luannv@8ck`

Cách B — Tạo thủ công bằng psql

```bash
# đăng nhập bằng user postgres (hoặc user có quyền)
psql -U postgres
# trong psql:
CREATE USER eoffice WITH PASSWORD 'luannv@8ck';
CREATE DATABASE eoffice OWNER eoffice;
\q
```

## Chạy ứng dụng

Chạy bằng Maven Wrapper (môi trường local):

```bash
# từ thư mục gốc project
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Hoặc build thành jar rồi chạy:

```bash
# build (bỏ test nếu muốn):
./mvnw clean package -DskipTests
# chạy jar (chú ý tên file trong target có thể khác):
java -jar target/eoffice-be-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Chạy bằng Docker (tùy chỉnh):

```bash
# build image (tùy Dockerfile có cấu hình sẵn)
docker build -t eoffice-be .
# chạy container, gắn port 8080
docker run -p 8080:8080 --env SPRING_PROFILES_ACTIVE=local --env SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/eoffice --env SPRING_DATASOURCE_USERNAME=eoffice --env SPRING_DATASOURCE_PASSWORD='luannv@8ck' eoffice-be
```

Hoặc sử dụng `docker compose` nếu bạn thêm service cho app (file `docker-compose.local.yml` hiện chỉ chứa Postgres):

```bash
# khởi động Postgres
docker compose -f docker-compose.local.yml up -d
# sau đó build image và chạy container app như bước trên
```

## Kiểm tra
- Health endpoint: `GET http://localhost:8080/public/health` — trả về `OK: <date>`
- Swagger / OpenAPI UI (nếu enabled):
  - Thử truy cập `http://localhost:8080/swagger-ui.html` hoặc `http://localhost:8080/swagger-ui/index.html`

## Kiểm thử
Chạy tests:

```bash
./mvnw test
```

## Thay đổi cấu hình
- Sử dụng profile `local` (ví dụ `--spring.profiles.active=local`) để dùng `application-local.yml`.
- Có thể đặt biến môi trường Spring Boot (ví dụ `SPRING_DATASOURCE_URL`) khi chạy Docker hoặc hệ thống.

## Ghi chú bảo mật
- Không lưu mật khẩu thật trong mã nguồn cho môi trường production. Sử dụng secret manager hoặc biến môi trường an toàn.

## Liên hệ
Nếu cần trợ giúp thêm, cho biết lỗi cụ thể (log, stacktrace) để dễ debug.


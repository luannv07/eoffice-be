# eoffice-be

Backend Spring Boot cho eOffice (Java 21).

## Environment Policy
- Chỉ dùng profile: `staging`, `prod`.
- Không dùng `application-local.yml`.
- Không commit file `.env` thật.

## Required
- Java 21
- Docker (nếu chạy bằng container)

## Run Local With Staging Profile
1. Tạo file `.env` từ mẫu:
```powershell
Copy-Item .env.staging.example .env
```
2. Điền giá trị thật cho `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.
3. Chạy app:
```powershell
.\scripts\run-staging.ps1
```

Script sẽ:
- nạp biến từ `.env`
- tự set `SPRING_PROFILES_ACTIVE=staging` nếu chưa có
- chạy `mvnw spring-boot:run`

## Build
```powershell
.\mvnw.cmd -DskipTests package
```

## Liquibase
- Master changelog: `src/main/resources/db/changelog/master.yml`
- Mỗi thay đổi schema thêm file mới trong `src/main/resources/db/changelog/changes/` và include vào `master.yml`.

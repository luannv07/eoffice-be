# Authenticated DAST (OWASP ZAP + JWT) — EPU E-Office

Nâng Pipeline 4 từ **unauthenticated baseline scan** lên **authenticated API scan** dùng
JWT context, theo `kich-ban-owasp-zap-dast-jwt-epu.docx` (đã hiệu chỉnh cho khớp code thật).

## 1. Thay đổi

| File | Nội dung |
|---|---|
| `eoffice-be/.zap/zap-jwt-config.yaml` | OWASP ZAP Automation Framework plan: login JSON → `data.accessToken`, header-based session management, OpenAPI import, spider + active scan (policy `API`), report JSON/HTML, gate `exitStatus: High`. |
| `eoffice-be/.github/workflows/pipeline-4-dast-scan.yml` | Chạy plan trên khi có `DAST_STAGING_USER`/`DAST_STAGING_PASS`; fallback về baseline scan khi chưa cấu hình; upload artifact + summary. |

## 2. Secrets cần thêm trên GitHub (Settings → Secrets → Actions)

| Secret | Bắt buộc | Ghi chú |
|---|---|---|
| `STAGING_BASE_URL` | khuyến nghị | App root, ví dụ `https://eoffice-be-dev.8chickensoffice.me/api/v1` (không có `/` cuối). |
| `HEALTH_CHECK_URL` | fallback | URL đầy đủ tới `/actuator/health`; workflow tự cắt hậu tố. |
| `DAST_STAGING_USER` | để bật authenticated scan | Tài khoản staging riêng, đã `profileCompleted = true`. |
| `DAST_STAGING_PASS` | để bật authenticated scan | Mật khẩu tài khoản trên. |

> Không dùng tài khoản admin thật hoặc tài khoản đã gắn chữ ký số PKI cho DAST.

### Tài khoản DAST đã cấu hình

| Thuộc tính | Giá trị |
|---|---|
| Username | `dast_scan` |
| Email | `dast.scan@epu.edu.vn` |
| Role | `USER` |
| `profileCompleted` | `true` (đã tạo `user_infos` qua `PUT /api/v1/profile/me`) |
| Tài khoản admin | **không** dùng, không biết credential, không tạo keystore PKI |

Tài khoản được tạo bằng `POST /api/v1/auth/register` rồi hoàn thiện hồ sơ bằng
`PUT /api/v1/profile/me` — không cần quyền admin.

**Hạn chế cần biết:** tài khoản này **chưa được gán chức danh (position)** nên theo mô hình
RBAC của dự án (`docs/system_seed.md`, "Đường 1: position") nó chưa có quyền nghiệp vụ.
Hệ quả trên staging: `GET /api/v1/documents/published` → 403.

→ Muốn phủ được business-flow thật, cần **gán chức danh** (ví dụ `CLERK`) cho `dast_scan`
trong màn hình Quản trị người dùng.

### Phát hiện phụ (không liên quan tới DAST) — nên xử lý riêng

`GET /api/v1/documents/pending` và `GET /api/v1/documents/statistics` trả **500
`INTERNAL_SERVER_ERROR`**. Đã đọc log Heroku để xác minh — **không phải lỗi nghiệp vụ**:

```
org.springframework.web.method.annotation.MethodArgumentTypeMismatchException:
  Method parameter 'id': Failed to convert value of type 'java.lang.String'
  to required type 'java.lang.Long'; For input string: "pending"
```

Hai URL đó không tồn tại nên request rơi vào `@GetMapping("/documents/{id}")`
(`DocumentController:90`). `GlobalExceptionHandler` **không** có
`@ExceptionHandler(MethodArgumentTypeMismatchException.class)` nên rơi vào handler
`Exception.class` → 500 thay vì 400/404.

Đề xuất: thêm handler cho `MethodArgumentTypeMismatchException` (trả 400 `BAD_REQUEST`)
và `NoHandlerFoundException`/`NoResourceFoundException` (trả 404). Đây cũng chính là loại
lỗi mà ZAP active scan phát hiện được, nên xử lý trước khi chốt số liệu DAST.


## 3. Cách chạy

- Tự động: schedule `0 3 * * *` (10:00 ICT) hoặc sau khi Pipeline 2 deploy thành công.
- Thủ công: `workflow_dispatch` trên `pipeline-4-dast-scan.yml`.
- Local (khi cần debug):
  ```bash
  docker run --rm -v "$PWD/zap_workspace:/zap/wrk:rw" \
    -e DAST_TARGET_URL=https://<host>/api/v1 \
    -e DAST_STAGING_USER=... -e DAST_STAGING_PASS=... \
    ghcr.io/zaproxy/zaproxy:stable \
    zap.sh -cmd -autorun /zap/wrk/zap-jwt-config.yaml
  ```

## 4. Khác biệt so với docx gốc (đã sửa)

| Docx | Thực tế trong codebase | Bản đã implement |
|---|---|---|
| Session management dùng `header` + `headerValuePrefix` + `headerValuePattern` | API thật là map `header: value` | `parameters: { Authorization: "Bearer {%json:data.accessToken%}" }` |
| Token JSON `{%json:$.data.accessToken%}` | `AuthUtils` lưu token dạng `json:<dotted.path>` (không có `$.`) | `{%json:data.accessToken%}` |
| Refresh endpoint `/auth/refresh-token` | Endpoint thật là `POST /auth/refresh`, refresh token nằm trong cookie HttpOnly (`Path=/auth`) | Bỏ refresh script thủ công; ZAP tự cookie + re-auth khi verification fail |
| Policy `API-Minimal-Security` | Tên chương trình của policy API là `API` | `policy: "API"` |
| Workflow dùng `action-baseline` với `-autorun` | `action-baseline` không chạy Automation Framework | `docker run … zap.sh -cmd -autorun` |
| Chỉ dựa vào spider | Spider không tìm thấy endpoint POST nghiệp vụ | Thêm job `openapi` import `/v3/api-docs` để seed sites tree |

## 5. Rủi ro cần lưu ý

- Active scan sẽ gửi payload vào các endpoint nghiệp vụ (`submit`, `approve`, `sign-and-publish`,
  upload…) trên **staging** với tài khoản DAST riêng → có thể tạo/đổi dữ liệu staging.
- `/auth/logout` bị loại khỏi context để không thu hồi refresh token của tài khoản scan.
- Nên pin digest cho image `ghcr.io/zaproxy/zaproxy:stable` khi chuyển sang môi trường thật.
- **Rate limit login**: `/auth/login` bị giới hạn 10 lần / 300 giây / IP
  (`rate-limit.endpoint-limit`, `rate-limit.endpoint-window-seconds`). ZAP đăng nhập 1 lần
  lúc bắt đầu và chỉ đăng nhập lại khi verification fail; nếu `loggedInRegex` không khớp
  thì ZAP sẽ đăng nhập lại liên tục và chạm giới hạn → scan fail. GitHub-hosted runner dùng IP
  dùng chung nên cũng có thể đã gần ngưỡng. Nếu gặp `RATE_LIMITED` trong log ZAP, hãy chạy
  scan bằng self-hosted runner hoặc nới `RATE_LIMIT_ENDPOINT_LIMIT` trên staging.
- Access token sống 15 phút, active scan tối đa 30 phút → ZAP sẽ phải re-authenticate
  giữa chừng; cơ chế này dựa trên cookie refresh token HttpOnly mà ZAP tự quản lý.
- **Staging đang thiếu ổn định về memory**: dyno Heroku chạy `-Xmx300m` và log ghi nhận
  `Error R14 (Memory quota exceeded)` (5 lần trong ~2 phút ngày 21/10). Nếu dyno restart
  giữa lúc quét, ZAP sẽ ghi nhận 503/timeout → false positive. Cần nâng dung lượng dyno
  hoặc giảm `-Xmx`/rò rỉ trước khi lấy số liệu DAST chính thức.

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

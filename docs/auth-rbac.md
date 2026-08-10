# Auth (JWT RS256) + RBAC nền tảng

**Branch:** `feat/jwt` — **Stack:** Spring Boot 4.0.6 / Java 21 / Spring Security 7 / PostgreSQL + Liquibase / H2 (test)
**Ngày:** 2026-08-09

## 1. Tổng quan

- Đăng nhập trả JWT trong JSON body (không dùng cookie), giao thức stateless.
- Access token ngắn hạn (15m) + refresh token dài hạn (30d); cả hai đều là JWT RS256.
- Permission chi tiết KHÔNG nhúng trong token — resolve từ DB tại runtime theo org scope.
- 3 role miền: `USER`, `ADMIN`, `SYSTEM_ADMIN` (xem `common/enums/Role.java`).

## 2. Luồng xác thực

### 2.1 Đăng nhập — `POST /auth/login`

Request:
```json
{ "username": "admin", "password": "secret" }
```

Xử lý (`auth/service/AuthService.java`):
1. Rate limit theo client IP (X-Forwarded-For → remoteAddr), mặc định 10 lần/300s (`LoginRateLimiter`, in-memory bucket).
2. Tìm `user_logins` theo username + `delete_flg=false`.
3. Kiểm tra khóa tài khoản (`account_locked` / `lockout_until`).
4. Verify password bằng BCrypt; sai → tăng `failed_login_attempts`, đạt 5 lần → khóa 30 phút.
5. Đúng → reset failed attempts, cập nhật `last_login_at`, sinh access + refresh token.

Response:
```json
{
  "code": "OK",
  "data": {
    "accessToken": "<jwt>",
    "refreshToken": "<jwt>",
    "expiresIn": 900,
    "user": {
      "id": 1, "username": "admin", "displayName": "...",
      "email": "...", "avatarUrl": null, "role": "ADMIN",
      "passwordExpiredAt": null
    }
  }
}
```

### 2.2 Thông tin người dùng hiện tại — `GET /auth/me`

Yêu cầu `Authorization: Bearer <accessToken>`. Trả về thông tin user từ claims + `user_infos`.

### 2.3 Ghi chú `/auth/refresh`

Được whitelist trong `SecurityConfig` nhưng **chưa implement** (deferred).

## 3. Cấu trúc token

Claims: `sub` (userId), `username`, `role`, `token_type` (`access`/`refresh`), `jti`, `iat`, `exp`, `iss`.

- Ký số: Nimbus `RSASSASigner` / `RSASSAVerifier`, thuật toán RS256, JWS compact.
- Parse: `JwtService.parseAccessToken` / `parseRefreshToken` — xác thực chữ ký + kiểm tra `token_type` + `requireIssuer`.

## 4. Cấu hình

`eoffice.jwt.*` (xem `jwt/config/JwtProperties.java` + `application.yml`):

| Key | Env | Mặc định |
|---|---|---|
| `eoffice.jwt.issuer` | — | `eoffice-be` |
| `eoffice.jwt.private-key` | `JWT_PRIVATE_KEY` | blank |
| `eoffice.jwt.public-key` | `JWT_PUBLIC_KEY` | blank |
| `eoffice.jwt.access-token-ttl` | `JWT_ACCESS_TTL` | `PT15M` |
| `eoffice.jwt.refresh-token-ttl` | `JWT_REFRESH_TTL` | `P30D` |
| `eoffice.jwt.auth-rate-limit` | — | `10` |
| `eoffice.jwt.auth-rate-window-seconds` | — | `300` |

> Nếu `private-key`/`public-key` blank, `JwtConfig` tự sinh RSA 2048 **dev key** mỗi lần khởi động. Production/staging phải set `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` (PKCS8 / X509 base64).

## 5. Security chain

`config/SecurityConfig.java`:
- CSRF disable, STATELESS, disable httpBasic + formLogin.
- Whitelist: `/public/**`, `/auth/login`, `/auth/refresh`, `/actuator/health/**`, Swagger.
- `JwtAuthenticationFilter` chạy trước `UsernamePasswordAuthenticationToken` — parse Bearer token, gán `JwtUserPrincipal` (userId, username, role) + authority `ROLE_<role>`.
- `@EnableMethodSecurity` + `MethodSecurityExpressionHandler` đăng ký `RbacPermissionEvaluator`.

## 6. Mô hình RBAC

- **Grant-only + union, không deny.** Mọi query luôn lọc `delete_flg=false`.
- **SYSTEM_ADMIN** bypass toàn cục (mọi permission, mọi org).
- **ADMIN** có global permission theo `RolePermissionCatalog` khi `orgNodeId == null`.
- Quyền ghép: permission từ **position** (org-scoped) ∪ permission từ **role** (global).

### 6.1 Vòng đời permission

1. `PermissionResolver.resolvePermissions(principal)` — tập hợp toàn bộ quyền.
2. `positionAssignmentRepository.findActiveAssignments(userId, now)` — assignment còn hiệu lực (`effective_from/effective_to`, `actual_dismissal_date IS NULL`).
3. `positionPermissionRepository.findGrantedPermissionCodes(orgId, positionCd)` — quyền trực tiếp tại node.
4. `positionPermissionRepository.findInheritedGrantedPermissionCodes(descendantOrgId, positionCd)` — quyền kế thừa từ tổ tiên qua `org_node_closures` (chỉ `inherit_to_children=true`).
5. `rolePermissionRepository.findPermissionCodesByRole(role)` — quyền theo role.

### 6.2 Permission codes

`auth/security/RolePermissionCatalog.java`:
- `org:read`, `org:manage`, `user:read`, `user:manage`, `audit:read`, `position:read`, `position:manage`.

### 6.3 Sử dụng trong controller

```java
@PreAuthorize("hasPermission(#orgNodeId, 'org:manage')")
```

`RbacPermissionEvaluator` → `PermissionResolver.hasPermission(principal, permissionCd, orgNodeId)`.

## 7. Cơ sở dữ liệu

### 7.1 Migration mới (đã include trong `master.yml`)

| File | Nội dung |
|---|---|
| `2026-08-09_add-shared-sequence.yml` | Sequence `hibernate_sequence` (start 1, increment 50) |
| `2026-08-09_add-auth-core-tables.yml` | `user_logins`, `user_infos`, `dictionary_items` + seed POSITION_CD / ORG_NODE_TYPE_CD |
| `2026-08-09_add-rbac-org-tables.yml` | `org_nodes`, `org_node_closures`, `position_assignments`, `position_permissions`, `role_permissions`, `position_degs` |

### 7.2 Entity (`entity/`)

- `UserLogin` — tài khoản đăng nhập (password hash, role, lockout, password expiry).
- `UserInfo` — profile bổ sung (email, phone, avatar, job_title, department).
- `DictionaryItem` — từ điển (position cd, org node type cd...).
- `OrgNode` — nút tổ chức, tự tham chiếu `parent_id`.
- `OrgNodeClosure` — closure table (`@IdClass` nested `OrgNodeClosureId` = ancestor_id + descendant_id, depth).
- `PositionAssignment` — gán người dùng vào vị trí tại một org node.
- `PositionPermission` — quyền theo vị trí tại một org node (grant, inherit).
- `RolePermission` — quyền theo role.
- `PositionDeg` — định nghĩa vị trí/ngạch.

ID dùng sequence chung `hibernate_sequence` (allocationSize 50) qua generator `eoffice_seq`.

### 7.3 Repository (`repository/`)

9 repository: `UserLogin`, `UserInfo`, `DictionaryItem`, `OrgNode`, `OrgNodeClosure`, `PositionAssignment`, `PositionPermission`, `RolePermission`, `PositionDeg` — chứa các query org-scoped/active (đã lọc `delete_flg`).

## 8. Dependencies mới (`pom.xml`)

- `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6`
- `com.nimbusds:nimbus-jose-jwt:10.0.1`
- `com.github.ben-manes.caffeine:caffeine` (managed version)

## 9. Error codes mới (`common/enums/ErrorCode.java`)

`INVALID_CREDENTIALS` (401), `ACCOUNT_LOCKED` (423), `RATE_LIMITED` (429), `INVALID_TOKEN` (401), `PASSWORD_EXPIRED` (401).

## 10. Còn lại / Deferred

- `/auth/refresh` — whitelist nhưng chưa có handler (được `JwtService.parseRefreshToken` hỗ trợ).
- Merge-node service, position_degs runtime re-check, user-level override.
- Task 11 (tests + H2 compatibility): người dùng tự build/fix ngoài nhánh commit này.

# 🏢 Nhóm Chức Năng 1: Tài Khoản & Tổ Chức (UC01 - UC04)

Tài liệu này chi tiết hóa hiện trạng triển khai, các phần còn thiếu và hướng dẫn kỹ thuật để hoàn thiện nhóm chức năng **Tài Khoản & Tổ Chức**.

---

## 1. Chi Tiết Từng Use Case

### 1.1. UC01: Đăng nhập và các trường hợp mở rộng
- **Tác nhân:** Người dùng hệ thống.
- **Hiện trạng:** 🟡 **Hoàn thành 70%**
  - ✅ **Đã hoàn thành:**
    - Đăng nhập hệ thống (`POST /api/v1/auth/login`): xác thực qua username/password, trả về Access Token + Refresh Token (HttpOnly Cookie).
    - Cấp lại token (`POST /api/v1/auth/refresh-token`).
    - Đổi mật khẩu (`POST /api/v1/auth/change-password`).
    - Khóa tài khoản tạm thời khi đăng nhập sai quá số lần quy định (`failed_login_attempts`, `lockout_until`).
  - ❌ **Chức năng còn thiếu:**
    - `[extend] Đăng ký tài khoản`: Hiện tại chưa có API đăng ký hoặc luồng tự đăng ký/xác nhận email.
    - `[extend] Quên mật khẩu`: Chưa có API yêu cầu cấp lại mật khẩu qua email (gửi OTP/Reset Token).
    - `[extend] Đăng xuất hệ thống`: Cần hoàn thiện API `POST /api/v1/auth/logout` để thu hồi (revoke) Refresh Token trong DB và xóa cookie.
  - 🎯 **Theo core của thầy (DevSecOps):**
    - **Ghi nhận Audit Log khi đăng nhập:** Mỗi lần đăng nhập (thành công/thất bại) cần ghi `AuditLog` bao gồm: IP Address, User-Agent, thiết bị, thời gian — phục vụ phát hiện truy cập bất thường (anomaly detection).
    - **Quản lý phiên làm việc:** Ghi nhận `session_id` hoặc `jti` (JWT ID) vào bảng `refresh_tokens` để có thể vô hiệu hóa (revoke) phiên cụ thể từ xa khi nghi ngờ lộ thông tin (hỗ trợ tính năng "Đăng xuất tất cả thiết bị").
    - **Rate Limiting:** Đã có cơ chế khóa tạm thời sau N lần sai (`failed_login_attempts`) — cần đảm bảo ghi log mỗi lần thất bại để phát hiện tấn công brute-force.

---

### 1.2. UC02: Quản lý tài khoản người dùng
- **Tác nhân:** Quản trị viên (ADMIN).
- **Hiện trạng:** 🔴 **Chưa làm API (Chỉ mới có DB)**
  - ✅ **Đã hoàn thành:**
    - Đã có thực thể `UserLogin` và `UserInfo` với đầy đủ trường thông tin (thông tin đăng nhập, hồ sơ cá nhân, xóa mềm `delete_flg`).
    - Repository `UserLoginRepository` và `UserInfoRepository` đã có các hàm truy vấn cơ bản.
  - ❌ **Chức năng còn thiếu (Chưa có Controller & Service):**
    - `[include] Xem danh sách tài khoản` (kèm phân trang, sắp xếp).
    - `[extend] Tìm kiếm và lọc tài khoản` (theo username, email, họ tên, trạng thái, phòng ban).
    - `[extend] Xem chi tiết tài khoản` (ghép nối `UserLogin` + `UserInfo` + danh sách vị trí công tác).
    - `[extend] Tạo mới tài khoản` (tạo đồng thời login và user_info).
    - `[extend] Chỉnh sửa thông tin tài khoản`.
    - `[extend] Khóa tài khoản / Mở khóa tài khoản` (chuyển đổi `user_status_cd` hoặc set `lockout_until`).
  - 🎯 **Theo core của thầy (DevSecOps):**
    - **Quản lý Chứng thư số / Thông tin chữ ký cá nhân:** Mỗi cán bộ có thẩm quyền duyệt/ký nháy/ký phát hành cần được cấu hình thông tin chứng thư số. Bổ sung trường `signature_image_url` (ảnh chữ ký mẫu) và `certificate_alias` (bí danh chứng thư số) vào `UserInfo` để phục vụ tích hợp ký số PDF ở UC13, UC14.
    - **Ghi Audit Log khi thao tác tài khoản:** Mọi thao tác Tạo/Sửa/Khóa/Mở khóa tài khoản đều phải ghi `AuditLog` (ai thực hiện, lúc nào, trên tài khoản nào, giá trị thay đổi trước/sau).

---

### 1.3. UC03: Quản lý đơn vị trực thuộc
- **Tác nhân:** Quản trị viên (ADMIN).
- **Hiện trạng:** 🟢 **Hoàn thành 85%**
  - ✅ **Đã hoàn thành:**
    - Thực thể `OrgNode` và bảng liên kết `OrgNodeClosure` để quản lý phân cấp cây tổ chức.
    - `OrgNodeController` & `OrgNodeService` đã cung cấp các API:
      - Xem danh sách cây tổ chức (`GET /api/v1/org-nodes/tree`).
      - Xem danh sách phẳng (`GET /api/v1/org-nodes`).
      - Tạo đơn vị mới (`POST /api/v1/org-nodes`).
      - Cập nhật thông tin đơn vị (`PUT /api/v1/org-nodes/{id}`).
      - Xóa đơn vị (`DELETE /api/v1/org-nodes/{id}`).
  - ❌ **Chức năng cần hoàn thiện/nâng cao:**
    - Tìm kiếm đơn vị theo tên, mã, loại hình (`type_cd`).
    - API chuyển đổi cấp bậc / di chuyển nhánh cây cha - con (Cập nhật lại toàn bộ quan hệ trong bảng `org_node_closures`).
  - 🎯 **Theo core của thầy (Luồng điều hành tự động):**
    - **Kéo thả / Re-parenting Node (Rất quan trọng):** Khi gắn nhánh (Bộ môn/Trung tâm) từ Khoa này sang Khoa khác, hệ thống phải tự động xóa các bản ghi ancestor cũ và tạo lại quan hệ mới trong `org_node_closures`. Vì `PermissionResolver` query **real-time từ DB**, toàn bộ quyền hạn nhân sự và luồng luân chuyển văn bản của nhánh đó sẽ **tự động cập nhật ngay lập tức** mà không cần cấu hình lại từng user — đây là điểm cốt lõi đáp ứng yêu cầu đề tài *"Linh hoạt thay đổi cơ cấu tổ chức, luồng điều hành tự động đổi theo"*.
    - **Hỗ trợ mô hình đa tầng thực tế của trường Đại học:**
      - Cấp 1 (Trường): Ban Giám Hiệu, Đảng ủy Trường, Hội đồng Trường.
      - Cấp 2 (Khoa/Phòng/Trung tâm): Quản lý Khoa, Chi bộ, Trưởng phòng.
      - Cấp 3 (Bộ môn/Tổ/Trung tâm trực thuộc): Trưởng Ngành/Tổ, Giám đốc Trung tâm.

---

### 1.4. UC04: Gán vai trò và phân quyền người dùng
- **Tác nhân:** Quản trị viên (ADMIN).
- **Hiện trạng:** 🟡 **Hoàn thành 50%**
  - ✅ **Đã hoàn thành:**
    - Entity `PositionAssignment`, `PositionDeg` (ủy quyền), `PositionPermission`, `RolePermission`.
    - `PermissionResolver` hỗ trợ tính toán quyền động dựa trên chức danh và cây đơn vị.
  - ❌ **Chức năng còn thiếu (Chưa có Controller/Service quản lý):**
    - `[include] Xem danh sách người dùng và vai trò / chức vụ`.
    - `[extend] Xem chi tiết quyền của người dùng`.
    - `[extend] Gán vai trò / chức vụ cho người dùng` (`PositionAssignment`: gán `position_cd` tại `org_node_id`, từ ngày đến ngày).
    - `[extend] Thay đổi / Thu hồi vai trò của người dùng` (cập nhật `actual_dismissal_date` hoặc `delete_flg = true`).
  - 🎯 **Theo core của thầy (Phân quyền phức hợp thực tế):**
    - **Một nhân sự kiêm nhiệm nhiều vai trò ở các cấp khác nhau:** Ví dụ: Một giảng viên vừa là Trưởng khoa CNTT (Cấp Khoa - có quyền duyệt văn bản Khoa CNTT), vừa là Ủy viên Hội đồng Trường (Cấp Trường - có quyền xem văn bản cấp Trường). `PositionAssignment` hỗ trợ điều này vì 1 `user_login_id` có thể có nhiều bản ghi gắn với các `org_node_id` khác nhau.
    - **Ghi Audit Log khi gán/thu hồi quyền:** Mọi thay đổi phân quyền cần ghi `AuditLog` đầy đủ để phục vụ điều tra và kiểm soát nội bộ.

---

## 2. Hướng Dẫn Hoàn Thiện Nhóm Chức Năng

### Bước 1: Bổ sung API Quản lý Người dùng (`UserController` & `UserService`)
1. **Tạo DTOs:**
   - `UserCreateRequest`: `username`, `email`, `password`, `fullName`, `phone`, `employeeCode`, `dob`, `gender`.
   - `UserUpdateRequest`: cập nhật thông tin cá nhân.
   - `UserFilterRequest`: `keyword`, `status`, `orgNodeId`, `page`, `size`.
   - `UserDetailResponse`: tổng hợp thông tin login, profile và danh sách các phòng ban/chức vụ đang giữ.
2. **Triển khai `UserServiceImpl`:**
   - Hàm `createUser`: mã hóa mật khẩu `BCrypt`, tạo bản ghi `UserLogin` và `UserInfo` trong 1 `@Transactional`.
   - Hàm `searchUsers`: sử dụng `JpaSpecificationExecutor` để lọc linh hoạt theo họ tên, username, email, trạng thái.
   - Hàm `toggleLockUser(Long userId, boolean lock)`: cập nhật `user_status_cd` thành `ACTIVE` hoặc `LOCKED`.
3. **Tạo `UserController`:**
   - Khai báo các endpoint chuẩn RESTful: `GET /api/v1/users`, `POST /api/v1/users`, `GET /api/v1/users/{id}`, `PUT /api/v1/users/{id}`, `PATCH /api/v1/users/{id}/status`.
   - Bảo vệ quyền bằng `@PreAuthorize("hasPermission(null, 'user:manage')")`.

### Bước 2: Hoàn thiện Logout & Quên mật khẩu trong `AuthController`
1. **Logout:**
   - Thêm endpoint `POST /api/v1/auth/logout`.
   - Lấy Refresh Token từ Cookie / Request Body -> gọi `refreshTokenService.revokeToken(refreshToken)`.
   - Xóa HttpOnly Cookie trả về cho client.
2. **Quên mật khẩu:**
   - Thêm `POST /api/v1/auth/forgot-password`: Kiểm tra email -> sinh mã Token reset (lưu Redis hoặc DB thời hạn 15 phút) -> gửi email chứa link đặt lại mật khẩu.
   - Thêm `POST /api/v1/auth/reset-password`: Xác thực token và cập nhật mật khẩu mới.

### Bước 3: Triển khai Quản lý Phân công Vị trí (`PositionAssignmentController`)
1. Tạo Controller `PositionAssignmentController`:
   - `POST /api/v1/position-assignments`: Gán người dùng vào vị trí/chức vụ tại phòng ban.
   - `PUT /api/v1/position-assignments/{id}`: Cập nhật thời hạn bổ nhiệm hoặc điều chuyển vị trí.
   - `DELETE /api/v1/position-assignments/{id}`: Thu hồi chức vụ (đánh dấu ngày miễn nhiệm `actual_dismissal_date`).
   - `GET /api/v1/position-assignments/user/{userId}`: Xem danh sách chức vụ của một cá nhân.

### Bước 4 (Theo core thầy): Bổ sung trường chữ ký số vào `UserInfo`
```java
// Bổ sung vào UserInfo entity:
@Column(name = "signature_image_url", length = 500)
private String signatureImageUrl; // URL ảnh chữ ký mẫu (dùng nhúng vào PDF ký nháy)

@Column(name = "certificate_alias", length = 100)
private String certificateAlias; // Bí danh chứng thư số trong Keystore (dùng cho ký số PKI)
```

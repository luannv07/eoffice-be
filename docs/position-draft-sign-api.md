# Gán quyền ký nháy cho chức vụ

Quyền gọi được người dùng xác nhận ngày 2026-11-19: ADMIN và SYSTEM_ADMIN.
Service đọc lại tài khoản đang hoạt động và role từ database, không dùng role JWT để quyết định.

`PUT /position-permissions/{positionCd}/draft-sign`

```json
{
  "inheritToChildren": false
}
```

- `positionCd` phải thuộc danh mục `POSITION_CD`, chưa xóa mềm và đang hoạt động; mã được chuẩn hóa về chữ hoa.
- `inheritToChildren` bắt buộc: `false` chỉ trong đơn vị bổ nhiệm, `true` gồm phạm vi đơn vị con theo PermissionResolver hiện có.
- Endpoint chỉ gán `DOCUMENT_SIGN_DRAFT`, không nhận mã permission tùy ý.
- Có quyền đang hoạt động thì cập nhật cùng bản ghi; chưa có thì tạo mới. Bản ghi đã xóa mềm được giữ nguyên.
- Khóa dòng danh mục chức vụ trong transaction để tuần tự hóa các lần gọi endpoint cho cùng chức vụ.
- Không thay đổi chức vụ, role, bổ nhiệm của tài khoản hoặc người được giao xử lý văn bản.
- Quyền áp dụng cho các bổ nhiệm chức vụ đang hiệu lực; ký nháy vẫn phải vượt kiểm tra người được giao và trạng thái workflow hiện có.

Response dùng BaseResponse với data gồm `id`, `positionCd`, `permissionCd`, `inheritToChildren`.
Không đăng nhập: 401; role không được phép hoặc tài khoản bị khóa: 403; chức vụ không hợp lệ hoặc thiếu dữ liệu: 400.

Endpoint này chưa bao gồm API thu hồi quyền hoặc danh sách quyền.

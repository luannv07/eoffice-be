# Triển khai người soạn duy nhất

Thay đổi này phá vỡ hợp đồng response cũ: bên gọi API văn bản phải dùng `drafterId`, `drafterUsername`, `drafterFullName`. Không có alias tương thích. Người soạn mới được gửi; admin không được gửi thay. Các quyền khác giữ nguyên.

## Trước triển khai

1. Chạy truy vấn chỉ đọc sau trên database đích và xuất kết quả để kiểm kê (bao gồm văn bản xóa mềm):

```sql
SELECT d.id, d.status, d.delete_flg, d.drafter_id, d.creator_user_id,
       CASE WHEN EXISTS (SELECT 1 FROM user_logins u WHERE u.id = d.drafter_id)
            THEN 'KEEP_DRAFTER'
            WHEN EXISTS (SELECT 1 FROM user_logins u WHERE u.id = d.creator_user_id)
            THEN 'COPY_CREATOR'
            ELSE 'UNKNOWN_AUTHOR' END AS migration_action
FROM documents d
ORDER BY d.id;
```

2. Sao lưu toàn bộ database và xác nhận có thể phục hồi. Dừng mọi instance ghi văn bản và mọi worker liên quan; không chạy ứng dụng cũ đồng thời với schema mới.
3. Kiểm thử migration trên bản sao database. `UNKNOWN_AUTHOR` chỉ được xóa nếu là DRAFT chưa từng gửi/ký/cấp số/phát hành, không có yêu cầu duyệt/workflow/phân phối/văn bản tham chiếu và chỉ có nhật ký soạn thảo. Migration báo các ID cần xác minh và dừng trước khi sửa dữ liệu nếu có bản ghi không đủ điều kiện xóa. Không tự gán người soạn.
4. Chỉ triển khai khi bản sao migrate thành công và bên gọi API đã được cập nhật. Triển khai ứng dụng và migration cùng đợt trong thời gian dừng ghi.

Migration ưu tiên người soạn hiện có, dùng người tạo cũ khi người soạn không hợp lệ, giữ tác giả lịch sử dù tài khoản xóa mềm/vô hiệu hóa. Tài khoản đó không được tự động kích hoạt lại. Migration xóa cứng dữ liệu con của nháp rác theo khóa ngoại, không xóa tệp trên storage. `drafter_id` trở thành NOT NULL, có khóa ngoại; cột người tạo cũ bị xóa. Các migration cũ giữ nguyên để không thay đổi checksum và để kiểm thử cài mới.

## Sau triển khai

- Kiểm tra tạo bản nháp và các response danh sách/chi tiết/cập nhật/cấp số chỉ có `drafter*` và đúng danh tính.
- Người soạn gửi hồ sơ đủ điều kiện thành công; admin gửi bản người khác nhận 403, không có chữ ký/yêu cầu duyệt mới.
- Kiểm tra constraint và xác nhận cột cũ đã biến mất. Theo dõi lỗi migration, 403 gửi duyệt và phản hồi từ các client API.
- Migration này không sửa các vấn đề PDF/ký tùy chọn, quyền người nhận hoặc gửi đồng thời.

## Phục hồi

Không có rollback tự tái tạo dữ liệu đã xóa hoặc người tạo cũ. Nếu cần quay lại, dừng ứng dụng mới, phục hồi backup database và phiên bản ứng dụng/client trước thay đổi cùng nhau. Không dùng ứng dụng cũ trên schema mới.

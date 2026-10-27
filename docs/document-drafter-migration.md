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

## Kiểm kê chỉ đọc ngày 2026-10-28 — issue 6

Đã chạy SELECT qua DBeaver trên kết nối cloud `d26tom0k353j5l`, database `d26tom0k353j5l`, schema `public` lúc khoảng 03:50 (UTC+7). Đây là kết quả của kết nối được kiểm tra, không phải xác nhận trạng thái của các kết nối localhost khác hoặc của database triển khai khác.

| Kiểm tra | Kết quả |
|---|---|
| Cột `documents.creator_user_id` | Còn tồn tại |
| `documents.drafter_id` cho phép null | Có |
| Changeset `2026-10-27-unify-document-drafter` | Chưa có bản ghi trong `databasechangelog` |
| Tổng văn bản, gồm xóa mềm | 41 |
| Giữ người soạn hợp lệ | 19 |
| Có thể chép tác giả hợp lệ từ cột cũ | 21 |
| Không xác định được tác giả và đủ điều kiện nháp rác | 0 |
| Không xác định được tác giả, cần xác minh | 1 — ID `1` |

Phân loại dựa trên cùng điều kiện tiền kiểm trong `UnifyDocumentDrafter`, bao gồm dấu vết ký/gửi/cấp số và các liên kết nghiệp vụ; không chỉ dựa vào trạng thái DRAFT. Tổng 22 văn bản thiếu người soạn hợp lệ gồm 21 bản có nguồn tác giả cũ hợp lệ và bản ID `1` chưa thể xác định tác giả. Chưa đọc đủ chi tiết để kết luận dấu vết cụ thể nào làm ID `1` không đủ điều kiện nháp rác.

**Chưa chạy migration, chưa cập nhật hoặc xóa dữ liệu/cột.** Theo tiền kiểm hiện có, ID `1` sẽ làm migration dừng; cần xác minh tác giả từ bằng chứng nghiệp vụ và thống nhất cách xử lý dữ liệu trước. Không tự gán tác giả, không xóa văn bản này và không DROP cột thủ công. Việc điều khiển DBeaver đã dừng khi người dùng nhấn Escape.

Chưa xác nhận bản sao lưu phục hồi được hoặc việc dừng toàn bộ tiến trình ghi. Sau khi giải quyết điểm chặn và đáp ứng điều kiện triển khai ở trên, phải kiểm kê lại vì dữ liệu có thể đã thay đổi, chạy migration đã có và xác minh cột cũ biến mất, `drafter_id` có NOT NULL/khóa ngoại, không còn tham chiếu tác giả không tồn tại; sau đó kiểm tra ứng dụng. Phần database của issue 6 chưa hoàn tất.

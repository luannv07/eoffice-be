# Issue 4 — Gửi lại theo PDF chính hiện hành

Trạng thái: **Chờ review** — bản cuối đạt test ngày 2026-11-04; chưa triển khai database dùng chung, còn chờ kiểm kê dữ liệu cũ.

## Quy tắc thay thế

Nguồn ký/gửi là đúng một attachment chính chưa xóa mềm, định dạng PDF, đọc được và không rỗng. Không truy vấn hoặc so sánh mốc trả về/ngày upload để cấp quyền gửi. Thu hồi/yêu cầu sửa phải vô hiệu hóa mọi main hiện hành và bỏ primaryFilePath trong cùng transaction; chưa có upload main mới thì không thể gửi. Không fallback về bản ký cũ hoặc phụ lục.

Chữ ký/lưu thành công vẫn là điều kiện trước khi thay đổi phiên bản/trạng thái, tạo yêu cầu duyệt và phát thông báo. Không so nội dung/hash để khẳng định người soạn đã sửa đúng comment.

Giữ query lịch sử trả về và log phục vụ chẩn đoán; không xóa API hoặc schema. Giữ sửa clear persistence context ở issue 3. Không thay logic upload/isMain, tuyến gửi lại, REJECTED, thẩm định hoặc chống đồng thời.

## Kiểm kê trước triển khai — bắt buộc

SQL chỉ đọc: `docs/sql/issue4-current-main-inventory.sql`. Chưa chạy trên database thật.

1. Người triển khai xác nhận đúng database/schema. Với PostgreSQL có thể kiểm tra bằng `SELECT current_database(), current_schema();`.
2. SQL liệt kê NEEDS_REVISION, REJECTED và DRAFT có submitted_at hoặc dấu vết yêu cầu duyệt, cùng attachment hiện hành/xóa mềm, đường dẫn bản ký và lịch sử trả về, gồm cả log xóa mềm. Có thể có nhiều dòng cho cùng hồ sơ vì ghép attachment với từng log.
3. Mọi kết quả đều cần xác minh; query không tự kết luận file nào là v2. Đặc biệt main cũ sau retire có is_main_document=false: không thể suy chỉ từ cờ này rằng một attachment xóa mềm trước đây là main hay phụ lục.
4. Không suy từ tên file/ngày upload/thiếu log rằng main hiện hành chắc chắn mới. Hồ sơ có v1 còn hiệu lực hoặc không đủ bằng chứng cần kế hoạch xử lý dữ liệu riêng, có sao lưu và phê duyệt; không cung cấp UPDATE/DELETE tự động trong đợt này.
5. Chỉ triển khai khi hồ sơ cũ đã được xác nhận an toàn hoặc xử lý theo kế hoạch riêng. Nếu chưa đủ bằng chứng, giữ trạng thái chờ triển khai. Việc code/test đạt không thay thế điều kiện này.

Phạm vi kiểm kê là các trạng thái có đường gửi lại hiện có, không tuyên bố đã kiểm toán toàn database hoặc giải quyết REJECTED.

## Kiểm thử

- Unit thay kỳ vọng từ chặn timestamp sang xét main hiện hành; xác nhận đường gửi không gọi query mốc trả về.
- Kiểm tra main đã xóa mềm/phụ lục bị loại ngay cả khi dependency trả về; lỗi không đọc storage hoặc gọi signer.
- Integration H2 riêng: thu hồi/yêu cầu sửa, mất log trả về, chưa upload/chỉ phụ lục bị chặn; upload v2 ký đúng bytes v2, v1 không được đọc lại. Giữ vòng v2/v3, rollback, cờ main và entity cache từ issue 3.
- Giữ các test quyền, định dạng, nhiều main, tệp rỗng/không đọc được và lỗi ký/lưu. PKI/storage dùng mock trong test nghiệp vụ.
- Nhóm liên quan đầu: 219 ca đạt, 0 failure/error/skipped, BUILD SUCCESS 01:17 phút. Ca SQL chạy riêng: 1 ca đạt, BUILD SUCCESS 45,530 giây.
- Bản cuối `.\mvnw.cmd clean test`: **1.775 ca, 0 failure/error/skipped, BUILD SUCCESS 03:47 phút**, kết thúc 2026-11-04 00:00:08 +07:00. Thêm 5 ca, không xóa test cũ; bốn kỳ vọng timestamp thay đổi theo quy tắc mới đã được người dùng chốt, giữ kiểm chứng quyền và không sử dụng tệp xóa mềm/phụ lục.
- Log bản cuối: `D:/code/e-office/current-main-issue4-full-2026-11-03.log`. SQL được chạy trên H2 trong integration và kiểm tra kết quả: loại hồ sơ mới chưa gửi/hồ sơ xóa mềm, liệt kê hồ sơ gửi lại cùng tệp cũ đã retire, tệp mới hiện hành và lịch sử; mọi dòng đều cần xác minh thủ công.
- `git diff --check` đạt. Không sửa upload, schema, API, query chẩn đoán hoặc issue khác; không database thật, commit hoặc push. H2 đạt không thay thế kiểm kê/kiểm chứng PostgreSQL đích trước triển khai.

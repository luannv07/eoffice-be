# Admin đọc toàn bộ văn bản, không xử lý nghiệp vụ

Ngày: 2026-10-29. Trạng thái: **Chờ review**.

## Quy tắc đã triển khai

- ADMIN và SYSTEM_ADMIN đang hoạt động được đọc/tìm mọi văn bản chưa xóa mềm ở mọi trạng thái, đơn vị, kể cả ngoài luồng. Role được đối chiếu database, không quyết định bằng claim role trong JWT.
- Quyền đọc gồm chi tiết, PDF/attachment, phiên bản/so sánh, lịch sử/timeline và kiểm định chữ ký; guard tệp liên kết cũng dùng quyền đọc này.
- Admin chỉ sửa/xóa/upload/restore và gửi/thu hồi yêu cầu của hồ sơ chính mình theo trạng thái hiện có. Ký xác nhận người soạn khi gửi vẫn được phép.
- Admin không ký thẩm định, duyệt, yêu cầu sửa, từ chối, cấp số, phát hành hoặc thu hồi văn bản đã phát hành, kể cả có phân nhiệm/ủy quyền hoặc là tác giả. Engine chặn trước permission resolver; các đường cấp số/phát hành cũ có guard service riêng.
- Không cho sửa/xóa tệp liên kết bằng API file-storage; thao tác tệp của mình phải qua API văn bản và điều kiện trạng thái tương ứng.
- Mô phỏng admin dev/test, nhãn hồ sơ test và guard ngăn cấp số/phát hành thật được giữ nguyên. Không mở tuyến HT tự soạn vào hàng chờ cho admin. Người nhận có role admin bị loại khỏi danh sách xử lý nghiệp vụ.

## Các điểm triển khai cần review

- DocumentReadPolicy giữ nguyên applies(Document) cho năm trạng thái của đợt A; ngoại lệ admin được kiểm tra riêng, tránh vô tình mở guard ghi hoặc quyền đọc cho role khác.
- DocumentRepository tách adminReadAll khỏi legacyGlobal; lọc trước phân trang/count. Các API danh sách chuyên biệt vẫn giữ bộ lọc trạng thái theo chức năng.
- Danh sách chờ kiểm tra role database tại service; controller chỉ yêu cầu xác thực để claim role cũ không quyết định ngoại lệ đọc admin.
- Tìm kiếm giữ thứ tự createdDate DESC và thêm id DESC khi thời gian trùng, khắc phục kết quả lặp/thiếu giữa các trang được test tích hợp phát hiện.
- API download cũ vẫn tải tệp chính thức theo hợp đồng; thiếu tham chiếu tệp vẫn trả lỗi storage hiện có. PDF đang làm việc được đọc qua API files/primary. Không tự đổi nguồn tệp.
- Quyền cấu hình sổ, tài khoản, tổ chức và permission resolver dùng chung không đổi. Các role khác giữ chính sách hiện có.

## Kiểm chứng

- Nhóm cuối liên quan: 271 test, 0 failure/error/skipped, 24 giây; log ngoài repository: D:/code/e-office/admin-readonly-targeted-6.log.
- Bản code cuối chạy `mvn -o -B ... clean test`: **1.622 test, 0 failure, 0 error, 2 skipped có sẵn**, BUILD SUCCESS, **01:07 phút**. Log: D:/code/e-office/admin-readonly-full-final-2.log.
- Tích hợp H2 riêng, OSIV tắt: cả hai role admin ở mọi trạng thái, JWT role khác database, tài khoản vô hiệu hóa/xóa, tài liệu xóa mềm, tìm kiếm/phân trang, đọc các alias, raw file guard và hàng chờ.
- Đối chứng phân nhiệm/ủy quyền thật: permission resolver trả quyền hợp lệ nhưng admin vẫn bị chặn; không gọi signer/storage, không đổi trạng thái hoặc ghi yêu cầu/log nghiệp vụ/sự kiện.
- Kiểm tra admin tự gửi bằng ký xác nhận rồi thu hồi của mình; admin không tự duyệt và không thu hồi yêu cầu người khác. Test nghiệp vụ dùng mock signer/storage; PKI chuyên biệt giữ nguyên.
- Không xóa test hoặc thêm skipped. Cập nhật kỳ vọng cũ đúng quy tắc mới; fixture cần người duyệt thực sự chuyển từ admin giả sang REVIEWER có phân nhiệm hợp lệ.

## Giới hạn

Không sửa frontend/schema/migration, không truy cập database thật, không commit/push. Không triển khai preflight, chống gửi đồng thời hoặc idempotency của phần B. Chưa kiểm thử thao tác giao diện hay triển khai thực tế. Chỉ đánh dấu hoàn thành sau xác nhận review của người dùng.

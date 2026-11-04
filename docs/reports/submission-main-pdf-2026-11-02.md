# PDF chính bắt buộc trước ký/gửi — 2026-11-02

Trạng thái: **Chờ review**. Thực hiện plan con người xác nhận; không commit/push, không database thật, không migration.

## Hành vi đã thay đổi

- Mọi lần submit phải vượt kiểm tra tài khoản database hoạt động, người soạn, trạng thái, tiêu đề/trích yếu, tuyến/người nhận trước ký. Người nhận gửi lại cũng phải ACTIVE; không cấp ngoại lệ ký/gửi thay cho admin.
- Có đúng một attachment chính chưa xóa mềm, định dạng PDF, tồn tại/đọc được/không rỗng. Text/phụ lục không thay thế main. Thiếu main trả `DOCUMENT_PRIMARY_FILE_MISSING`/HTTP 400, thông báo “Vui lòng tải lên tệp PDF chính trước khi gửi duyệt.” Nhiều main báo dữ liệu hồ sơ không hợp lệ.
- Nguồn ký là attachment chính hiện hành, không đọc primaryFilePath của lượt trước và không fallback. Lỗi đọc v2 chặn gửi, không quay lại v1/phụ lục. Không sửa bộ upload/validator/storage hoặc cách truyền isMain.
- Thu hồi/yêu cầu sửa retire main cũ bằng JPQL update deleteFlg=true, isMainDocument=false; bỏ primaryFilePath cùng transaction. Không dựa vào việc gán @SoftDelete rồi giả định repository.delete sẽ đúng. Không xóa vật lý, không retire phụ lục. REJECTED giữ helper đóng băng cũ, chưa đổi thành trạng thái cuối.
- Kiểm tra thời điểm upload lớn hơn mốc trả về mới nhất từ process log RECALL_APPROVAL/workflow log REQUEST_REVISION; không suy phiên bản từ tên file. Với NEEDS_REVISION hoặc DRAFT đã gửi mà thiếu mốc trả về, không cho gửi khi chưa chứng minh được bản mới. **Dữ liệu cũ thiếu mốc trả về cần xác minh dữ liệu; chỉ upload lại không bổ sung được mốc lịch sử đã thiếu.** Không đoán hoặc tự sửa database.
- Ký bắt buộc trên mọi tuyến; kết quả phải có bytes/hash, storage phải trả reference không rỗng. Sau đó mới tăng phiên bản (khi áp dụng), đổi trạng thái, lưu yêu cầu duyệt/log/event. Giao dịch rollback giữ dữ liệu cũ; dùng cleanup upload sẵn có của storage.

## Thay đổi kỹ thuật

Ba file production: DocumentApprovalService, DocumentAttachmentRepository, DocumentWorkflowServiceImpl. Không đổi URL/request/response, schema hoặc dependency constructor. Repository bổ sung cập nhật retire main và đọc mốc trả về; dùng cấu trúc bảng hiện có.

Các test cũ được cập nhật fixture có PDF chính đọc được thay cho text/primaryFilePath; giữ kiểm tra quyền, actor, trạng thái, log, event, rollback và cấp số. Assertion cũ về nguồn primaryFilePath chỉ giữ cho đường cấp số; submit kiểm tra source attachment mới. Không xóa test. Bổ sung 38 ca so với audit baseline 1.636 ca.

## Kiểm chứng

| Lượt | Kết quả |
|---|---|
| Unit approval/first-submit/ownership/submission-numbering/workflow/attachment sau cập nhật fixture | 285 ca, 0 failure/error/skipped; 29,189 giây |
| Integration PhaseA + routing | 56 ca, 0 failure/error/skipped; 01:11 phút |
| First-submit sau thêm các ca lỗi đọc/disabled sender | 76 ca đạt; bản cuối thêm 1 ca disabled recipient được chạy trong toàn suite |
| Toàn suite bản cuối `clean test` | **1.674 ca, 0 failure, 0 error, 2 skipped có sẵn; BUILD SUCCESS; 03:47 phút** |
| Kiểm tra diff | `git diff --check` đạt |

Log cuối: `D:/code/e-office/submission-pdf-full-2026-11-02.log`. Kết quả XML Surefire xác nhận FirstSubmission 77 ca, PhaseAIntegration 53 ca, SubmissionRoutingIntegration 3 ca đều đạt trên bản cuối.

Hai skipped không thay đổi: FileStorageServiceTest.symlinkTraversalAttack_shouldThrowStorageError và storeFile_plantedSymlinkIntermediate_shouldNotCreateDirectoryOutsideRoot.

Các ca mới kiểm tra: thiếu/chỉ phụ/text/main xóa mềm/nhiều main/main không PDF; v2 mất/rỗng/không đọc được/provider lỗi; sender/recipient bị vô hiệu; signer trả null/bytes/hash lỗi hoặc ném lỗi; storage reference rỗng/null hoặc ném lỗi; stale/không biết thời điểm upload/trả về; thứ tự đọc→ký→lưu→bàn giao; không gọi readPdf(v1).

Integration dùng H2 UUID, OSIV tắt, không transaction bao ngoài test: upload thực qua attachment service với storage giả lập; submit/recall/requestRevision thực; query SQL xác nhận v1 soft-delete nhưng bản ghi còn, phụ lục còn, v2/v3 đúng bytes vào signer; rollback khôi phục trạng thái/attachment/reference/mốc trả về; ký/lưu lỗi không tăng phiên bản hoặc tạo request. HTTP thiếu main trả 400 đúng mã lỗi.

## Giới hạn

- Signer/storage được mock trong test nghiệp vụ; kiểm tra mật mã thật tiếp tục ở nhóm PKI hiện có, không thêm sinh khóa hoặc encode/decode PKCS12 vào fixture nghiệp vụ. Không đổi cấu hình production hoặc PKI test.
- Chưa thử PostgreSQL/provider thật hoặc concurrency. Không khẳng định toàn vòng đời hết lỗi.
- F03 REJECTED, lock/idempotency, quyền/phân công thẩm định và phát hành vẫn là việc riêng. Giữ tệp trên storage không tạo chức năng lịch sử PDF từng lượt gửi.
- Tài liệu quyền/lessons/kế hoạch chung ở `D:/code/e-office/docs`, ngoài Git repository backend. Báo cáo này nằm trong repository để review/commit khi người dùng yêu cầu.

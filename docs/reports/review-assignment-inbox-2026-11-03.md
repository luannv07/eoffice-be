# Thẩm định đích danh và hộp thư cá nhân — 2026-11-03

Trạng thái: **Chờ review**. Toàn suite bản cuối đạt; chưa tự đánh dấu hoàn thành nghiệp vụ.

## Vấn đề và thay đổi

Trước đây permission theo chức vụ/đơn vị đủ để người cùng khoa xử lý văn bản được giao cho người khác. Bổ sung `ReviewAssignmentPolicy` tại WorkflowEngine cho bốn hành động trong `IN_REVIEW`: `INITIAL_SIGN`, `REVIEW_APPROVE`, `REQUEST_REVISION`, `REJECT`.

- Đối chiếu tài khoản đang hoạt động trong database và người nhận đích danh của yêu cầu hiện hành; vẫn phải có permission nghiệp vụ. Admin không được xử lý thay.
- Yêu cầu hiện hành là bản mới nhất theo `submittedAt DESC, id DESC`, gồm cả bản ghi xóa mềm khi xác định thứ tự. Sau đó bắt buộc bản này chưa xóa, `PENDING`, có thời điểm gửi và người nhận hợp lệ. Không hồi sinh yêu cầu cũ khi bản mới bị xóa/thu hồi/xử lý.
- Sai người nhận: HTTP 403, `FORBIDDEN`, “Văn bản không được giao cho bạn xử lý.” Thiếu yêu cầu hợp lệ: HTTP 400, `INVALID_WORKFLOW_STATE`, thông báo cần kiểm tra luồng.
- Service kiểm tra trạng thái/quyền trước đọc PDF, signer, storage hoặc ghi dữ liệu. Lookup đơn vị chủ trì trong thao tác duyệt cũng nằm sau guard.
- Không dùng ủy quyền để thay ID người được giao; giữ PermissionResolver và quyền ủy quyền chung.

## API hộp thư

```http
GET http://localhost:8080/api/v1/documents/review-inbox?page=0&size=10
Authorization: Bearer <accessToken>
```

Trả `BaseResponse<Page<PendingDocumentResponse>>`, chỉ gồm văn bản chưa xóa ở `IN_REVIEW` với yêu cầu hiện hành `PENDING` giao đúng tài khoản database. Lọc và count trước phân trang. Thứ tự cố định theo thời điểm gửi giảm dần rồi ID yêu cầu giảm dần; không nhận thứ tự tùy ý từ client. SLA tính từ thời điểm gửi của yêu cầu hiện hành.

FE có thể dùng API này cho mục “Chờ tôi xử lý”. `/pending-review` giữ danh sách tồn đọng hiện có. Người soạn, người từng tham gia và admin tiếp tục đọc theo policy cũ; quyền đọc không cấp quyền xử lý. Admin không có văn bản trong hộp thư này chỉ nhờ role quản trị.

## Kiểm chứng

- `ReviewProcessingAuthorizationTest`: 69 ca, engine/service thật với repository, signer và storage giả lập. Kiểm tra sáu role × bốn hành động ở cả engine và service, quyền nghiệp vụ, dữ liệu yêu cầu hỏng, tài khoản database khác JWT và không có tác dụng phụ khi bị từ chối.
- `ReviewInboxIntegrationTest`: 24 ca đạt, Spring/HTTP/security/repository thật; H2 riêng theo UUID, OSIV tắt, không transaction bao ngoài test. Kiểm tra đúng/sai người được giao, bốn hành động, phân trang/count, lịch sử đọc, request mới nhất/xóa mềm/trùng thời điểm, thay người sau khi tải danh sách, tài khoản vô hiệu hóa và ủy quyền không thay người nhận.
- Test nghiệp vụ dùng mock signer/storage, không sinh khóa hoặc đóng/mở PKCS12 để chuẩn bị.
- Các lỗi setup đã gặp và sửa đúng fixture: `OrgNode.nameEn`, `Document.documentTypeCd` bắt buộc theo Liquibase; miễn nhiệm thiếu lý do vi phạm constraint. Không đổi schema hoặc giảm assertion.
- Bản cuối chạy `.\mvnw.cmd clean test`: **1.767 test, 0 failure, 0 error, 0 skipped, BUILD SUCCESS, 04:00 phút**. Không xóa test cũ; thêm 93 ca. Ba test cũ chỉ cập nhật constructor engine với dependency mới; các ca mới dùng policy thật để kiểm chứng guard.
- Trong cùng lượt chạy: `ReviewProcessingAuthorizationTest` 69 ca/0,290 giây; `ReviewInboxIntegrationTest` 24 ca/7,387 giây; `PhaseAIntegrationTest` 53 ca/17,27 giây. Thời gian toàn suite bao gồm clean, biên dịch và khởi tạo context.
- Log: `D:\code\e-office\review-assignment-full-2026-11-03.log`. `git diff --check` đạt. Nhóm mục tiêu ban đầu 249 ca cũng đạt; ca ủy quyền chạy riêng đạt sau khi sửa fixture.

## Giới hạn

Không đổi request lifecycle, bàn giao HT/PHT, thiết kế REJECTED, concurrency/idempotency hoặc frontend. Ký nháy riêng vẫn giữ `IN_REVIEW`; duyệt chuyển tiếp vẫn chuyển `PENDING_APPROVAL` theo engine hiện có, chưa tự tạo bàn giao lãnh đạo trong thay đổi này. Không thêm migration, API quản lý ủy quyền, không thao tác database thật, commit hoặc push. H2 không thay thế kiểm chứng PostgreSQL triển khai thực tế.

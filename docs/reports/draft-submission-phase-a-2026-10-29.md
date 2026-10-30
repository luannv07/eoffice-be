# Đợt A — Quyền đọc, đơn vị dự thảo và người nhận

Ngày: 2026-10-29. Trạng thái: **Chờ review** — code và kiểm thử đã đạt; chưa có xác nhận review A.

Nguồn nghiệp vụ: các xác nhận của người dùng và `D:/code/e-office/docs/permission-matrix.md`; kế hoạch `D:/code/e-office/docs/plans/draft-submission-five-tasks.md`.

## Thay đổi đã triển khai

### Quyền đọc và quyền sửa

- `DocumentReadPolicy` dùng tài khoản hiện hành trong database, không tin role JWT. Chỉ người soạn hoặc sender/approver của bất kỳ yêu cầu duyệt chưa xóa mềm thuộc đúng hồ sơ được đọc.
- Policy mới áp dụng đúng năm trạng thái: `DRAFT`, `IN_REVIEW`, `PENDING_APPROVAL`, `NEEDS_REVISION`, `REJECTED`. Không giới hạn lịch sử ở request PENDING/mới nhất; request xóa mềm không cấp quyền.
- Tích hợp kiểm tra ở service trước truy vấn nội dung con/storage: chi tiết, danh sách dự thảo/hàng chờ thẩm định, attachment/PDF, phiên bản/so sánh, timeline và kiểm định chữ ký.
- Người có quyền đọc lịch sử không được sửa, restore hoặc upload/xóa tệp của người soạn khác. Admin không có ngoại lệ ở các nhánh này.
- Danh sách dùng EXISTS trong truy vấn để giữ phân trang. API file-storage dùng guard cho liên kết metadata/reference tới hồ sơ; danh sách tệp nạp metadata, hồ sơ và lịch sử theo lô, không gọi kiểm tra riêng từng tệp. Tệp không liên kết văn bản và các trạng thái ngoài phạm vi giữ nhánh hiện có.

### Đơn vị và tuyến gửi

- `GET /documents/draft-options` trả đơn vị được phép cùng `issuingOrgRequired` và `adminSimulationEnabled`.
- Resolver dùng phân nhiệm còn hiệu lực tại thời điểm đánh giá, loại đơn vị/phân nhiệm đã xóa, hết hạn, chưa hiệu lực hoặc miễn nhiệm; dữ liệu mơ hồ bị từ chối.
- USER thuộc khoa/phòng chỉ chọn đúng đơn vị mình, không phụ thuộc riêng mã CV/GV. Gọi trực tiếp bằng JSON chọn đơn vị khác nhận 403.
- Tuyến gửi thẳng được bỏ trống đơn vị. Nếu có đơn vị phân nhiệm duy nhất thì dùng đơn vị đó; nếu không có thì lưu null cả hai quan hệ đơn vị, không tự gán mặc định. HT/PHT dùng đơn vị cấp trường được phân nhiệm.
- Options và gửi lần đầu cùng dùng resolver. REVIEW của USER theo đơn vị database; REVIEW mô phỏng của admin chỉ có TK/PK hoặc TP/PP tại đơn vị đã chọn. Gửi sai người nhận trong tuyến REVIEW nhận 403 trước signer/storage.
- Các tuyến lãnh đạo, chống tự chọn chính mình và quy tắc duy nhất HT/PHT tiếp tục được kiểm tra bởi routing hiện có. Đợt này không thay nghiệp vụ xử lý của người nhận.

### Mô phỏng admin và dữ liệu test

- `eoffice.workflow.admin-simulation-enabled` mặc định false. Chỉ bật khi mọi active profile thuộc dev/test; staging/production/profile hỗn hợp hoặc thiếu profile làm cấu hình bật bị từ chối ngay khi khởi động.
- Cả ADMIN/SYSTEM_ADMIN vẫn phải là người soạn. Mô phỏng không đổi role, không cấp quyền đọc hồ sơ người khác và không mở CLERK_QUEUE.
- Migration mới thêm `documents.is_test_document NOT NULL DEFAULT false`, đăng ký ở master và master-test. Dữ liệu cũ giữ false; không sửa migration lịch sử.
- Tạo/sửa/gửi bằng ngoại lệ mô phỏng đánh dấu hồ sơ test và ghi log actor/hồ sơ/đơn vị/tuyến/người nhận tương ứng. Không có request xóa nhãn test; response giai đoạn đầu có `testDocument`.
- Ba đường cấp số/phát hành (`assignNumber`, `publishDocument`, `signAndPublish`) chặn hồ sơ test trước tăng số, ký phát hành hoặc lưu dữ liệu nghiệp vụ. Không thiết kế lại sổ/phát hành.

## Kiểm thử

Các lượt kiểm thử trong đợt này được lưu riêng ở `D:/code/e-office/phase-a-*.log`:

| Lượt | Kết quả |
|---|---|
| Nhóm policy/routing/controller/tích hợp sau sửa fixture | 273 test đạt |
| Nhóm bổ sung file theo lô, migration, hàng chờ, chặn cấp số/phát hành | 204 test đạt |
| Tệp/PDF/phiên bản với policy thật thay mock policy cũ | 76 test đạt |
| Toàn suite clean build trước cập nhật cuối | 1.532 test, 0 failure/error, 2 skipped; Maven khoảng 1 phút 2 giây |
| Toàn suite sau cập nhật test quyền | 1.532 test, 0 failure/error, 2 skipped; Maven 51,752 giây |
| Kiểm tra cuối trên phiên bản bàn giao | **1.532 test, 0 failure, 0 error, 2 skipped; BUILD SUCCESS; Maven 55,072 giây** (`phase-a-final-verification.log`) |

- Role × trạng thái được kiểm tra cho cả sáu role; admin/leader/clerk không có quyền mặc định ở năm trạng thái A.
- Tích hợp dùng Spring service/security/repository thật, H2 UUID riêng, OSIV tắt, không transaction bao ngoài test. Kiểm tra mọi trạng thái request duyệt, request xóa mềm, khác hồ sơ, giả role JWT, danh sách có phân trang, PDF/attachment/version/compare, restore và đường vòng file-storage.
- Migration được kiểm tra cài mới qua context/changelog test và nâng cấp riêng changeset trên H2: giữ dữ liệu cũ, default false cho dữ liệu mới, cấm null và chạy lại không làm mất bản ghi.
- Các test nghiệp vụ dùng mock storage/signer tại ranh giới, không sinh CA hoặc đóng/mở PKCS12 để chuẩn bị. Bộ test PKI thật đã tối ưu vẫn được chạy trong toàn suite.
- Hai ca skipped là các kiểm tra symlink có sẵn của `FileStorageServiceTest`: `symlinkTraversalAttack_shouldThrowStorageError` và `storeFile_plantedSymlinkIntermediate_shouldNotCreateDirectoryOutsideRoot`. Đợt A không thêm skip hoặc thay điều kiện bỏ qua của chúng.
- Các lỗi trung gian đã được ghi vào lessons: fixture thiếu trường NOT NULL, mock truy vấn cũ, thiếu fixture đăng nhập và kỳ vọng quyền admin cũ. Không nới schema hoặc bỏ test để vượt lỗi; các kỳ vọng quyền thay theo nghiệp vụ mới đã được người dùng chốt.

Lệnh chạy toàn suite trong môi trường này (PowerShell, JDK 21, Maven offline):

```powershell
mvn -o -B "-DargLine=-javaagent:C:\Users\admin\.m2\repository\net\bytebuddy\byte-buddy-agent\1.15.11\byte-buddy-agent-1.15.11.jar" test
```

## Hợp đồng cho frontend / kiểm tra Postman

Ví dụ chạy local, mọi request nghiệp vụ dùng Bearer token của đúng tài khoản. ID dưới đây là ví dụ; lấy ID thực từ response/options trên database local.

1. `GET http://localhost:8080/api/v1/documents/draft-options` — frontend dùng `data.issuingOrgs` cho danh sách đơn vị; không tự lấy cây toàn trường thay thế.
2. `POST http://localhost:8080/api/v1/documents/drafts` — ví dụ người dùng có đơn vị 100:

```json
{
  "title": "Dự thảo thử nghiệm local",
  "summary": "Kiểm tra đơn vị và quyền đọc",
  "content": "Nội dung dự thảo",
  "documentTypeCd": "CONG_VAN",
  "issuingOrgNodeId": 100
}
```

Tuyến thẳng được truyền `issuingOrgNodeId: null`; USER tuyến REVIEW không được bỏ đơn vị bắt buộc. Client không được điều khiển `testDocument`.

3. Với ID trả về, ví dụ 16002: `GET http://localhost:8080/api/v1/documents/16002/submission-options`. Dùng nguyên routes/approvers hợp lệ; REVIEW chỉ hiện người thuộc đơn vị được phép.
4. Upload PDF vẫn là `POST http://localhost:8080/api/v1/documents/16002/attachments`, multipart `file` và `isMain`; giữ nghiệp vụ PDF-only của phần 1.
5. Gửi qua `POST http://localhost:8080/api/v1/documents/16002/submit-approval`:

```json
{
  "route": "REVIEW",
  "approverUserId": 200,
  "submitNote": "Kính trình thẩm định"
}
```

Thay 200 bằng người nhận trong options. Thử sửa JSON sang người/đơn vị khác phải nhận 403 ở tuyến REVIEW; tài khoản ngoài luồng gọi chi tiết/PDF/version cũng phải bị từ chối. Người đã tham gia được xem nhưng restore/sửa/upload thay người soạn bị chặn. Kiểm tra giao diện thực tế vẫn cần frontend tích hợp, chưa được thực hiện trong workspace backend này.

## Giới hạn bàn giao

- `APPROVED`, `PENDING_SIGN`, `PUBLISHED`, `ISSUED`, `REVOKED` giữ nhánh đọc/văn thư/phân phối cũ. Đặc biệt không coi quyền PUBLISHED theo phạm vi nơi nhận đã được sửa trong A.
- Không triển khai B: chưa chuẩn hóa toàn bộ preflight/PDF/ký-lưu, chưa khóa đồng thời hoặc có idempotency receipt. Không kết luận đã chống gửi hai lần.
- Không đổi nghiệp vụ thu hồi, thẩm định, gửi lại/REJECTED/NEEDS_REVISION, ủy quyền hoặc nhận việc văn thư. Chỉ có guard chặn hồ sơ test tại các đường cấp số/phát hành.
- Chưa chạy migration trên PostgreSQL/database thật, chưa deploy, chưa kiểm thử frontend/Postman thủ công, chưa commit hoặc push.
- Sau test đạt chuyển **Chờ review**; chỉ ghi **Hoàn thành** khi người dùng xác nhận review A.

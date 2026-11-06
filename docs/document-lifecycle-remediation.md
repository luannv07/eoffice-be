# Backlog hoàn thiện luồng văn bản đến ban hành

Rà soát ngày 2026-11-04, mốc mã nguồn `92ade22`. Tài liệu dùng để chia đợt sửa, review và nghiệm thu từ lúc lập dự thảo đến khi văn bản chính thức được phát hành và người nhận có thể tra cứu.

**Cách đọc.** “Lỗi trong code” là hành vi xác nhận từ mã nguồn hoặc probe đã ghi trong báo cáo. “Cần kiểm chứng” là rủi ro cần tái hiện trên cấu hình phù hợp. “Cần chốt” là quyết định nghiệp vụ thuộc người phụ trách. Các ô dưới đây là việc còn mở.

## 1. Luồng và mốc đã có

Luồng chính đi từ `DRAFT → IN_REVIEW → PENDING_APPROVAL → APPROVED → PUBLISHED`. Văn thư nhận việc, cấp số và ký phát hành trong đoạn từ `APPROVED` đến `PUBLISHED`. Hai tuyến gửi đầu đặc biệt đi thẳng tới `PENDING_APPROVAL` hoặc hàng chờ văn thư ở `APPROVED`, theo tư cách người soạn và phân nhiệm. `PENDING_SIGN` có trong enum và API đọc nhưng chưa được nối đầy đủ vào đường chuyển trạng thái hiện hành.

- Tạo dự thảo và gửi lần đầu đã có kiểm tra quyền sở hữu, đơn vị và tuyến gửi. PDF upload qua luồng văn bản đã có giới hạn định dạng và kiểm tra ở storage.
- Gửi dùng đúng một PDF chính còn hiệu lực. Thu hồi/yêu cầu sửa vô hiệu hóa tệp chính cũ và bỏ tham chiếu bản đã ký trong cùng giao dịch. Issue 4 bỏ phụ thuộc mốc trả về khi gửi lại. Điều kiện triển khai với dữ liệu cũ nằm ở mục 5.
- `IN_REVIEW` đã kiểm tra đúng người nhận hiện hành và có `GET /documents/review-inbox`. Hộp thư lọc theo giao việc trước khi phân trang, sắp theo `submittedAt DESC, id DESC`. Quyền đọc của người từng tham gia và quyền đọc của admin thuộc chính sách riêng.
- Mốc kiểm thử đã ghi nhận gồm 1.775 ca đạt ở lượt clean sau Issue 7 và 59 ca Phase A đạt sau Issue 9. Lượt lập tài liệu này đối chiếu code và báo cáo. Nghiệm thu từng mục bên dưới cần bằng chứng riêng.

[Báo cáo Phase A](reports/draft-submission-phase-a-2026-10-29.md) · [Hộp thư thẩm định](reports/review-assignment-inbox-2026-11-03.md) · [PDF chính và dữ liệu cũ](reports/submission-current-main-issue4-2026-11-03.md)

## 2. Việc cần làm theo công đoạn

### Dự thảo, tệp chính và gửi duyệt

- [ ] **A1 — Hồi quy tạo/sửa/restore/upload.** Kiểm tra người soạn, trạng thái được chỉnh sửa và đơn vị từ phân nhiệm ở các đường ghi. Bao phủ tuyến cho phép trống đơn vị, hồ sơ mô phỏng, file giả đuôi PDF, file rỗng, nhiều main, chỉ phụ lục và thao tác thay tệp thất bại. Kiểm tra cả API lẫn service.
- [ ] **A2 — Chống gửi trùng.** Rà điều kiện đọc trạng thái và cập nhật văn bản trong `submitForApproval`. Cần kiểm chứng hai request gửi đồng thời và retry sau khi client mất response. Một lượt gửi chỉ tạo một kết quả nghiệp vụ, gồm phiên bản, chữ ký, giao việc và event tương ứng. Chốt cơ chế khóa và hợp đồng retry trước khi sửa.
- [ ] **A3 — Thu hồi và gửi lại đúng vòng.** Đối chiếu yêu cầu hiện hành ở thu hồi, inbox và xử lý quyết định. Hiện thu hồi lấy bản mới nhất chưa xóa mềm, trong khi hộp thư xét cả bản đã xóa khi xác định bản mới nhất. Kiểm tra trường hợp thiếu request, trùng timestamp, request mới nhất đã đóng/xóa và người nhận đã bắt đầu xử lý. Gửi từ `NEEDS_REVISION` giữ ID và bắt buộc main mới. Sau khi chốt quy tắc chọn người nhận cho vòng sửa, áp dụng quy tắc đó vào tuyến gửi lại.
- [ ] **A4 — Đóng `REJECTED` (F03).** Engine và đường gửi vẫn cho tái sử dụng hồ sơ bị từ chối. Chặn chỉnh sửa, upload, restore, ký, thu hồi về nháp và gửi lại cùng ID theo quy tắc đóng hồ sơ. Giữ lý do, chữ ký và lịch sử để đọc theo quyền. Hồ sơ đề xuất lại dùng ID mới.

### Thẩm định và phê duyệt lãnh đạo

- [x] **B1 — Bắt buộc PDF và chữ ký ở bước duyệt (F04).** *(Đã sửa 2026-11-05)* Đã bổ sung `ensureDocumentPdfExists(document)` và `pdfSigner` guard ngay đầu `approve()`, loại bỏ `loadDocumentPdfBytesSafely` để bắt buộc phải có chữ ký hợp lệ khi chuyển trạng thái duyệt.
- [ ] **B2 — Ký nháy rồi chuyển tiếp (R1).** `initial-sign` giữ `IN_REVIEW`, sau đó `approve` gọi ký lại cho cùng người. Kiểm chứng chuỗi trên cùng PDF với signer thật. Chốt một trong hai trải nghiệm: gộp ký và chuyển tiếp, hoặc xác minh chữ ký thẩm định hiện có rồi chuyển tiếp. Phân biệt người ký, mục đích ký và phiên bản PDF.
- [ ] **B3 — Hoàn tất yêu cầu và bàn giao (R2, F06).** Khi duyệt, yêu cầu sửa hoặc từ chối, cập nhật yêu cầu hiện hành và lý do/thời điểm xử lý. Khi chuyển lãnh đạo, tạo yêu cầu mới đúng HT/PHT trong cùng giao dịch với trạng thái và audit log. Kiểm tra lãnh đạo thấy hồ sơ trong danh sách phù hợp và mở được PDF. `assignedOrgNodeId` là đơn vị chủ trì, cần trường hoặc quy tắc riêng cho người nhận kế tiếp.
- [ ] **B4 — Guard cấp lãnh đạo và chống tự duyệt (F07).** Guard đích danh hiện giới hạn ở `IN_REVIEW`. Bổ sung kiểm tra đúng người nhận/vòng ở `PENDING_APPROVAL`, cả tuyến chuyển tiếp và tuyến gửi thẳng. Chặn tự duyệt theo ma trận. Đối chiếu role gate, phân nhiệm và permission để người được giao có thể thực thi đúng action, bao gồm quyền ký khi thao tác duyệt có ký.

### Văn thư, cấp số và ban hành

- [ ] **C1 — Văn thư tiếp nhận và cấp số.** Hàng chờ dùng chung đang dựa trên `APPROVED`. Chốt thời điểm nhận việc và người chịu trách nhiệm phát hành, cách đổi người và cách xử lý hồ sơ do văn thư tự soạn. Rà tài khoản active, quyền lấy từ DB, sổ còn mở, loại/đơn vị/năm, số trùng và tái cấp. Lock sổ hiện có bảo vệ bộ đếm của sổ. Cần test cùng một văn bản được cấp số từ hai sổ và tranh chấp giữa cấp số với phát hành.
- [ ] **C2 — Thống nhất hai API phát hành (F08).** *(Đã xử lý nhánh DRAFT 2026-11-05)* Đã chặn hoàn toàn trạng thái `DRAFT` trong `DocumentPublishService.publishDocument()`, chỉ cho phép `APPROVED`. Việc còn lại: rà soát role gate `CLERK` cho `/workflow/sign-and-publish` và thống nhất hợp đồng gọi từ FE.
- [ ] **C3 — Chốt thời điểm ký, niêm phong và commit.** Xác nhận việc cấp số, cập nhật ngày và đóng dấu trên PDF diễn ra ở bước nào. `PUBLISHED` phải đi kèm số hợp lệ, PDF chính thức đã lưu, hash của đúng bytes được lưu và audit log. Kiểm tra chuỗi chữ ký qua các lần ghi PDF và DocMDP ở bước cuối. Tệp ghi trước commit cần được dọn khi rollback. Kiểm thử lỗi storage, lỗi DB sau ghi tệp, retry và tình huống tiến trình dừng giữa chừng.
- [ ] **C4 — Phân phối và quyền đọc sau phát hành.** *(Đã sửa lỗi phân phối rỗng 2026-11-05)* Đã loại bỏ reflection sai tên trường trong `DocumentPublishService.extractTargetIds()`, chuyển sang đọc trực tiếp `PublishRequest.getRecipientOrgIds()`. Việc còn lại: rà soát chính sách phân quyền đọc/tải PDF sau phát hành.

**Đối chiếu code.** Các điểm A/B nằm trong `DocumentApprovalService`, `ReviewAssignmentPolicy`, `DocumentWorkflowServiceImpl` và `WorkflowEngine`. Các điểm C nằm trong `DocumentBookServiceImpl`, `DocumentPublishService`, `DocumentWorkflowController` và `PublishRequest`. Đối chiếu lại theo tên lớp/phương thức khi dòng mã thay đổi. [Audit thẩm định](reports/document-review-workflow-audit-2026-10-28.md) · [Audit vòng đời ban đầu](../../docs/reports/document-lifecycle-audit-2026-10-31.md)

## 3. Quy tắc khi sửa

1. **Quyền theo hành động và dữ liệu hiện tại.** Lấy danh tính từ phiên đăng nhập, kiểm tra tài khoản active, role và phân nhiệm trong DB. Kiểm tra ở service. Quyền xem, quyền được liệt kê, quyền được giao và quyền ký là các điều kiện riêng. Áp ma trận theo người soạn, đơn vị, vòng, trạng thái và action. Giữ ranh giới admin đọc và ngoại lệ tự gửi hồ sơ mình đã được chốt.
2. **Kiểm tra trước tác dụng phụ.** Xác định trạng thái, quyền, PDF, người nhận, điều kiện sổ và dữ liệu bắt buộc trước ký hoặc ghi tệp. Sau khóa hoặc trước ghi có điều kiện, kiểm tra lại dữ liệu có thể đã thay đổi. `currentVersion` là phiên bản nghiệp vụ, cần cơ chế riêng để bảo vệ cạnh tranh ghi.
3. **Giữ đúng nguồn PDF.** Bản gửi lại lấy attachment chính còn hiệu lực. Bản đã ký dùng cho bước sau phải thuộc đúng hồ sơ và vòng xử lý. Giữ bằng chứng của bản đã retire theo quyền đọc lịch sử. Sau bulk update có clear context, lấy entity từ kết quả lưu hoặc nạp lại trước khi đọc quan hệ lazy.
4. **Chuyển giao nguyên tử.** Trạng thái văn bản, kết quả request, người nhận mới, số và bản ghi phân phối phải nhất quán với giao dịch tương ứng. Event bên ngoài chỉ phát sau commit. Nếu cần bảo đảm gửi thông báo sau crash, định nghĩa cơ chế lưu và retry sự kiện rồi kiểm thử theo cam kết đó.
5. **Giữ hợp đồng trong lúc chuyển đổi.** Kiểm kê caller của endpoint cũ, trạng thái và dữ liệu có sẵn trước khi đổi API/schema. Mỗi thay đổi quyền có test role × action. Giữ test cũ, sửa kỳ vọng chỉ khi quy tắc mới đã được chốt. Khi phát hiện lỗi thật hoặc test fail, ghi nguyên nhân và bài học theo quy ước của dự án.

## 4. Các quyết định cần chốt trước khi triển khai từng đợt

| Quyết định                            | Phạm vi cần làm rõ                                                                                                                                                             |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Tuyến lãnh đạo                        | Người thẩm định chuyển cố định lên HT hay chọn HT/PHT. Đối chiếu các ghi chú cũ và tuyến gửi đầu hiện chạy, tránh lấy quy tắc của tuyến đầu áp sang bàn giao.                  |
| Vòng sửa và thu hồi                   | Gửi lại cho người yêu cầu sửa hay cho chọn trong phạm vi hợp lệ. Xử lý khi người đó nghỉ hoặc hết quyền. Ký nháy riêng có được tính là đã bắt đầu xử lý để chặn thu hồi không. |
| Cấp nào được từ chối hoặc yêu cầu sửa | Quyền theo trạng thái và vòng. Chốt việc trả về sau cấp số và cách xử lý số đã cấp nếu nghiệp vụ cho phép.                                                                     |
| Văn thư nhận và ký                    | Cơ chế nhận độc quyền, chuyển người, quy tắc tự xử lý, thời điểm cấp số và có cần dùng `PENDING_SIGN` hay không.                                                               |
| Nơi nhận và độ mật                    | Quyền tra cứu/tải theo người nhận, đơn vị, mức độ mật và admin. Hành vi của các đường đọc cũ khi chuyển sang chính sách mới.                                                   |

Khi ma trận thiếu hoặc mâu thuẫn, ghi `NEED_HUMAN` cho đúng mục và đưa tình huống cụ thể để chốt. Nguồn quy tắc trong workspace là `D:/code/e-office/docs/permission-matrix.md` và `D:/code/e-office/AGENTS.md`. Các báo cáo ngày cũ dùng để truy nguyên phát hiện, cần đối chiếu lại với code mới trước khi mở issue.

## 5. Thứ tự triển khai và nghiệm thu

1. **Chốt nghiệp vụ và chặn đường đi tắt.** Ưu tiên F08, REJECTED và điều kiện bắt buộc PDF/chữ ký. Chốt tuyến lãnh đạo và đường phát hành chuẩn trước khi đổi hợp đồng.
2. **Làm trọn chuỗi thẩm định.** Giải quyết ký lặp, trạng thái request, giao việc cho lãnh đạo, chống tự duyệt và quyền đọc sau bàn giao. Sau đó nối hàng chờ văn thư với cấp số, ký và phân phối.
3. **Kiểm chứng cạnh tranh và lỗi giữa chừng.** Dùng PostgreSQL kiểm thử với hai transaction độc lập cho gửi/gửi, duyệt/từ chối, thu hồi/duyệt, cấp số/cấp số và phát hành/phát hành. Mỗi ca đối chiếu cả Document, request, counter, audit, tệp và event. Dùng PDF thật để kiểm tra chuỗi chữ ký, storage lỗi và niêm phong. Các ca nghiệp vụ còn lại có thể dùng signer/storage giả lập.
4. **Kiểm kê trước phát hành bản sửa.** Chạy [SQL chỉ đọc Issue 4](sql/issue4-current-main-inventory.sql) trên đúng môi trường được phép, xác minh PDF cũ còn hiệu lực và hồ sơ thiếu lịch sử. SQL đã chạy trên H2, còn việc xác minh dữ liệu đích. Các hồ sơ chưa đủ bằng chứng cần kế hoạch xử lý dữ liệu riêng trước rollout.
5. **Chạy E2E bằng cùng một hồ sơ.** Tạo và upload v1 → gửi → thẩm định → yêu cầu sửa → upload v2 → gửi lại → bàn giao lãnh đạo → văn thư nhận/cấp số → ký → phân phối → người nhận mở và xác minh PDF. Thử thêm tuyến gửi thẳng, thao tác sai người, lỗi giữa chừng và endpoint cũ. Chỉ đánh dấu hoàn thành khi có kết quả test cụ thể, review và bằng chứng các điểm còn mở đã được xử lý.

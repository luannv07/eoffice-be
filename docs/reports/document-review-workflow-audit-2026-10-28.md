# Báo cáo rà soát: người nhận thẩm định, duyệt chuyển tiếp, yêu cầu sửa và từ chối

Ngày: 2026-10-28. Mã nguồn được kiểm tra: commit `eb0cd034c35a188236407fcf6abceb21f1059f8b`.

## 1. Phạm vi và cách đọc kết quả

- Rà luồng sau khi người soạn gửi dự thảo và văn bản ở `IN_REVIEW`: người nhận mở hồ sơ/PDF, ký nháy, duyệt chuyển tiếp, yêu cầu chỉnh sửa hoặc từ chối.
- Không sửa code nghiệp vụ, test trong `src/test`, ma trận quyền, migration hoặc database thật. Báo cáo này không phải quyết định triển khai sửa lỗi.
- Chức vụ người nhận tạm thời được người dùng mô tả là Trưởng khoa/Phó trưởng khoa. Quy tắc USER thuộc khoa nào, ai được nhận, chuyển từ khoa lên ai và các ngoại lệ quản trị còn chờ con người xác nhận; không tự suy diễn thành ma trận chuẩn.
- `ACCEPT`: xác nhận được hành vi/vấn đề từ code hoặc kiểm chứng, đề nghị đưa vào đợt sửa sau. `NEED_HUMAN`: cần quyết định nghiệp vụ/phân quyền trước khi chọn cách sửa. Mức P1/P2 là đề xuất để AI reviewer phản biện, không phải kết quả kiểm thử xâm nhập production.

### Phương pháp kiểm chứng

1. Đọc controller, service, engine, repository, DTO, seed quyền và các test liên quan.
2. Chạy 6 nhóm test hiện có: `DocumentWorkflowServiceTest`, `DocumentWorkflowServiceIntegrationTest`, `DocumentWorkflowControllerTest`, `WorkflowEngineTest`, `PermissionResolverTest`, `DocumentApprovalServiceTest`. Kết quả: **212 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**.
3. Chạy harness tạm `target/review-audit/WorkflowReviewProbe.java`: dùng **DocumentWorkflowServiceImpl, WorkflowEngine và recallApproval thật**, nhưng repository, PermissionResolver, storage và PdfSigner là mock. Có 7 kiểm chứng đạt; không thay file test trong repository để tạo kết quả này.

**Giới hạn:** harness không đi qua HTTP/method-security proxy, không ký PDF bằng mật mã thật, không đo race bằng database thật và không kiểm tra UI. Nó chứng minh các nhánh/ordering của service với đầu vào được kiểm soát, không thay thế test tích hợp transaction/storage hoặc test đồng thời. Lượt này không chạy lại toàn bộ suite và không khảo sát dữ liệu tài khoản/quyền trên database đích.

## 2. Luồng code đang thực hiện

Các URL dưới đây có tiền tố API `/api/v1` theo môi trường ứng dụng.

| Thao tác | Endpoint | Trạng thái/hiệu ứng hiện tại |
|---|---|---|
| Gửi lần đầu | `POST /documents/{id}/submit-approval` | `DocumentApprovalService` lưu văn bản `IN_REVIEW`, tạo yêu cầu duyệt `PENDING` với `approverUserId` cụ thể |
| Danh sách chờ | `GET /documents/pending-review` | Lấy theo tập trạng thái, hiện không lọc người được chỉ định hay khoa của người gọi |
| Xem hồ sơ/lịch sử | `GET /documents/{id}`, `/{id}/workflow-logs`, `/{id}/timeline` | Có các nhánh kiểm tra quyền đọc không thống nhất; xem D3 |
| Ký nháy riêng | `POST /documents/{id}/workflow/initial-sign` | Ký PDF và ghi log `INITIAL_SIGN`; trạng thái vẫn `IN_REVIEW` |
| Duyệt chuyển tiếp | `POST /documents/{id}/workflow/approve` khi `IN_REVIEW` | Tự gọi ký nháy nếu nạp được PDF, rồi `REVIEW_APPROVE` → `PENDING_APPROVAL` |
| Duyệt nội dung cấp sau | Cùng endpoint `approve`, khi `PENDING_APPROVAL` | Chọn `LEADER_APPROVE` → `APPROVED`, tự ký hiển thị nếu có PDF |
| Yêu cầu sửa | `POST /documents/{id}/workflow/request-revision` | `NEEDS_REVISION`, xóa con trỏ PDF hiện hành, bỏ cờ tệp đính kèm chính |
| Từ chối | `POST /documents/{id}/workflow/reject` | `REJECTED`, đóng băng tệp theo cách trên; người soạn hiện vẫn có thể sửa và gửi lại |

`assignedOrgNodeId` trong request approve là **đơn vị chủ trì/bút phê**, không phải ID người duyệt tiếp theo. Request này không có trường chọn người nhận bước kế tiếp.

Thông báo gửi ban đầu hiện đi qua `DocumentSubmittedEvent`; listener đang ghi log và để chỗ tích hợp WebSocket/email, chưa phải bằng chứng đã có cơ chế giao thông báo tới người nhận. Chưa thấy sự kiện giao người nhận tiếp theo trong đường approve đang rà.

## 3. Những điểm đang làm đúng

- Service lấy ID người thao tác từ phiên đăng nhập rồi đọc `UserLogin` từ database; role dùng trong engine là role tài khoản đọc lại, không chỉ dùng role trong JWT.
- WorkflowEngine có bảng chuyển trạng thái và kiểm tra permission theo đơn vị văn bản. Các hành động quyết định không được engine cấp quyền khi không xác định được đơn vị.
- `reject`/`requestRevision` gọi engine kiểm tra trạng thái và lý do trước khi đóng băng tệp; controller có validation lý do bắt buộc.
- Storage đã đăng ký cleanup khi transaction rollback. Không kết luận mọi lỗi sau khi upload đều làm rò rỉ file vĩnh viễn.
- Role ADMIN/SYSTEM_ADMIN được controller cho đi vào route không có nghĩa engine tự động cấp quyền nghiệp vụ; `RolePermissionCatalog` không cấp global permission workflow cho chúng.
- Mapper chi tiết gọi lookup hồ sơ đơn lẻ không tự động là N+1. Không lặp lại issue N+1 đã bị bác ở luồng chi tiết.

## 4. Phát hiện kỹ thuật

### R1 — Ký nháy riêng xong có thể không duyệt chuyển tiếp được [P1, ACCEPT]

**Bằng chứng:**

- `src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:321-384`: `initialSign` gọi `signBytes`, lưu PDF rồi thực hiện `INITIAL_SIGN`.
- `src/main/java/vn/edu/epu/eoffice_be/workflow/WorkflowEngine.java:42-48`: `INITIAL_SIGN` giữ nguyên `IN_REVIEW`.
- `src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:145-185`: `approve` ở `IN_REVIEW` tiếp tục gọi `signBytes` cho cùng actor, không kiểm tra bước ký trước đó.
- `PdfSigner.signWithCustomKeys` chặn trường `sig_user_<userId>` đã tồn tại bằng `DuplicateSignatureException`; exception này kế thừa `IllegalStateException`.

**Chuỗi tái hiện:** nhận văn bản → gọi initial-sign thành công → vẫn IN_REVIEW → cùng người bấm approve → bộ ký gặp chữ ký cũ → service trả BAD_REQUEST, chưa chuyển PENDING_APPROVAL. Harness `PROBE_4` tái hiện với bộ ký giả lập exception đúng hợp đồng; không ký PDF thật trong probe.

**Tác động:** người thẩm định đã ký có thể bị kẹt ở bước duyệt tiếp. Test hiện có kiểm tra initial-sign và approve riêng lẻ nên không bảo đảm chuỗi hai thao tác này hoạt động.

**Hướng reviewer xem xét:** thống nhất ký và chuyển bước trong một thao tác, hoặc nhận biết chữ ký thẩm định đã có trên đúng phiên bản và không ký lại. Không chỉ bắt lỗi trùng rồi bỏ qua mọi chữ ký, vì phải phân biệt chữ ký người soạn, người thẩm định và phiên bản PDF.

### R2 — Trạng thái yêu cầu duyệt không đồng bộ với quyết định workflow [P1, ACCEPT]

**Bằng chứng:**

- `src/main/java/vn/edu/epu/eoffice_be/service/DocumentApprovalService.java:281-291`: tạo `DocumentApprovalRequest(PENDING)` và lưu người được chỉ định.
- `src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:121-249, 297-317, 536-555`: approve, requestRevision và reject không cập nhật yêu cầu duyệt, `processedAt` hoặc `decisionNote`.
- `src/main/java/vn/edu/epu/eoffice_be/workflow/WorkflowEngine.java:160-198`: engine chỉ lưu Document và DocumentWorkflowLog.
- Tìm toàn bộ `src/main/java`: không thấy xử lý dùng `ApprovalRequestStatus.APPROVED/REJECTED` hoặc `setDecisionNote`; `setProcessedAt` hiện chỉ xuất hiện trong recall.

**Tác động xác định từ code:** văn bản đã có quyết định nhưng yêu cầu gửi ban đầu vẫn PENDING. Khi sửa/gửi lại, có thể còn nhiều yêu cầu PENDING ở các vòng cũ. Kiểm tra quyền tải PDF trong `DocumentAttachmentService.java:196-198` xét **bất kỳ** yêu cầu PENDING nào, nên người nhận cũ có thể tiếp tục được nhánh này cấp quyền đối với PDF hiện tại, kể cả sau khi vòng sau chọn người khác. Chính sách lưu quyền xem lịch sử cần chốt riêng; không nên vô tình dùng PENDING cũ để biểu diễn quyền lịch sử.

**Hệ quả đã kiểm chứng:** initial-sign thành công không đổi IN_REVIEW/PENDING; người soạn vẫn gọi recall về DRAFT được (`PROBE_7`, dùng recall service thật). Có cho thu hồi sau chữ ký thẩm định hay không cần xác nhận nghiệp vụ; hiện tiêu chí kiểm tra “đã xử lý” không phản ánh sự kiện ký này.

**Hướng sửa để reviewer lập plan:** đồng bộ quyết định, thời điểm và ý kiến vào đúng yêu cầu/vòng xử lý trong cùng transaction; xác định trạng thái khi bắt đầu xử lý, ký, trả sửa, từ chối và gửi lại. Không tự chọn một request cũ bằng cách đoán.

### R3 — Có thể duyệt chuyển tiếp mà không có kết quả ký PDF [P1 nếu bắt buộc ký, ACCEPT hành vi / NEED_HUMAN quy tắc]

**Bằng chứng:** `DocumentWorkflowServiceImpl.java:145-149, 187-189, 235-245, 267-277`. Helper `loadDocumentPdfBytesSafely` chuyển lỗi DOCUMENT_PRIMARY_FILE_MISSING thành null; khi bytes null/rỗng, nhánh ký bị bỏ qua nhưng `executeTransition` vẫn chạy. PDF chính có reference nhưng storage lỗi khác vẫn được truyền ra, không phải mọi lỗi đọc file đều bị nuốt.

**Kiểm chứng:** `PROBE_1` xác nhận IN_REVIEW, không PDF → PENDING_APPROVAL, không gọi signer. Đây cũng là hành vi tương ứng với một số test approve thành công hiện có không chuẩn bị PDF.

**Tác động:** trạng thái duyệt tiếp không chứng minh chữ ký thẩm định thành công. Các bản legacy/đường gửi cho phép thiếu PDF hiện có có thể đi qua nhánh này.

**Cần chốt:** thẩm định có bắt buộc ký PDF không hay có loại duyệt nội dung không ký? Yêu cầu kiểm tra đủ điều kiện trước ký của người soạn không tự thay thế quyết định nghiệp vụ cho giai đoạn thẩm định. Nếu bắt buộc, phải có guard hồ sơ, ký thành công và lưu kết quả trước khi chuyển bước; chưa thay assertion nào trong lượt audit.

### R4 — initialSign kiểm tra trạng thái sau khi ký và gọi storage [P2, ACCEPT]

**Bằng chứng:** `DocumentWorkflowServiceImpl.java:326-375` chỉ kiểm tra permission trước xử lý PDF. `validateActorPermission` không kiểm tra bảng chuyển trạng thái; `validateTransition` chỉ được gọi trong `executeTransition` ở `WorkflowEngine.java:170`.

**Kiểm chứng:** `PROBE_5`: actor có quyền ký, document ở APPROVED; service đã gọi signer và storeSignedPdf rồi mới nhận WORKFLOW_TRANSITION_NOT_ALLOWED.

**Tác động:** action vốn không được thực hiện vẫn gây xử lý PKI/IO. Transaction và cleanup có thể hoàn tác dữ liệu/tệp nhưng không làm mất chi phí đã phát sinh; probe không chứng minh file thật bị lưu vĩnh viễn.

**Hướng sửa:** kiểm tra trạng thái và các guard có thể xác định trước ở service trước mọi thao tác ký/lưu; engine vẫn kiểm tra lại lúc áp dụng chuyển trạng thái. Đây không phải đề xuất bỏ kiểm tra ở engine.

### R5 — Hai đường xem hồ sơ/timeline không thống nhất [P2, ACCEPT mâu thuẫn / NEED_HUMAN phạm vi đọc]

**Bằng chứng:** `DocumentWorkflowServiceImpl.java:615-634` cho mọi role REVIEWER/LEADER đọc chi tiết/lịch sử mà không đối chiếu khoa hoặc người nhận; overload timeline có Authentication ở `:933-975` chạy guard khác, ưu tiên ADMIN/SYSTEM_ADMIN/CLERK hoặc người soạn/văn bản PUBLISHED, không có nhánh người nhận thẩm định. Controller timeline dùng overload có Authentication (`DocumentWorkflowController.java:129-136`).

**Kiểm chứng:** `PROBE_6`: cùng actor REVIEWER, cùng document IN_REVIEW không phải của mình → đọc detail được, gọi overload timeline của route thì AccessDeniedException.

**Tác động:** người làm review có thể đọc nội dung nhưng không xem được tiến trình qua timeline; ngược lại quyền đọc detail hiện có thể rộng hơn danh sách người nhận/khoa mong muốn. Không tự kết luận quyền xem toàn hệ thống là hợp lệ hay phải thu hẹp khi ma trận còn chờ.

**Hướng xử lý:** chốt một chính sách xem cho người được giao, chức vụ, khoa, cấp trên và dữ liệu mật; dùng thống nhất cho detail, workflow logs, timeline và tải PDF. Cần xét cả dữ liệu trong DB, không chỉ authorities của JWT.

### R6 — Rủi ro hai quyết định đồng thời, chưa có kiểm chứng race [P1 tiềm ẩn, ACCEPT rủi ro từ code]

**Bằng chứng:** service đọc Document bằng `findByIdAndDeleteFlgFalse` (`DocumentWorkflowServiceImpl.java:637-639`), engine kiểm tra trạng thái trong bộ nhớ rồi save. Không thấy pessimistic lock trên truy vấn này, compare-and-set theo trạng thái, hoặc `@Version` trong Document/BaseEntity. `currentVersion` là phiên bản nghiệp vụ, không phải optimistic locking của JPA.

**Kịch bản cần test:** hai transaction cùng đọc IN_REVIEW; một approve, một reject, hoặc hai approve. Chúng có thể cùng qua guard trên trạng thái cũ và ghi các quyết định/log/PDF xung đột.

**Mức xác nhận:** mới rà code, **chưa chạy race trên PostgreSQL**, chưa chứng minh lịch xen kẽ hoặc kết quả commit cụ thể. `@Transactional` không tự bảo đảm chỉ một thao tác thắng. AI reviewer cần xác nhận bằng test hai transaction độc lập trước khi kết luận mức độ ảnh hưởng.

## 5. Các quyết định nghiệp vụ/phân quyền đang chờ — không tự sửa

### D1 — Quyền xử lý có buộc đúng người đã được chọn không? [NEED_HUMAN]

- Lúc gửi có `approverUserId` cụ thể; lúc review, engine kiểm tra `DOCUMENT_APPROVE` hoặc `DOCUMENT_SIGN_DRAFT` theo org, **không đọc người nhận của yêu cầu duyệt** (`WorkflowEngine.java:122-152`).
- `getPendingDocuments(Pageable)` ở `DocumentWorkflowServiceImpl.java:998-1010` và query `DocumentRepository.java:117-129` lọc trạng thái, không lọc người nhận/khoa. Danh sách này có thể là màn hình giám sát toàn hệ thống theo thiết kế cũ; không mặc định đó là inbox cá nhân.
- Approve chỉ đổi trạng thái/ghi bút phê, không chọn hoặc tạo giao việc cho người kế tiếp. `DocumentApproveRequest` không có nextApproverUserId; assignedOrgNodeId là đơn vị chủ trì.

**Cần con người chốt:** chỉ đúng người được chọn được quyết định hay trưởng/phó có thể xử lý thay trong khoa? Bước sau định tuyến cho cá nhân nào, tự chọn Hiệu trưởng theo bổ nhiệm hay yêu cầu chọn? Nếu muốn cơ chế xử lý thay, phải định nghĩa rõ phạm vi và lưu dấu thay, không suy từ role quản trị.

### D2 — Chọn được người nhận chưa bảo đảm thực thi được action [NEED_HUMAN]

Ba lớp đang không tương đương:

1. Danh sách người nhận dựa trên mã HT/PHT/TK/PK/TP/PP và bổ nhiệm, không kiểm tra đầy đủ permission hành động (`DocumentApprovalService.java:70-115, 409-450`). Còn fallback admin khi không tìm thấy người.
2. Controller approve/reject/initial-sign chặn bằng role REVIEWER/LEADER/ADMIN/SYSTEM_ADMIN (`DocumentWorkflowController.java:66-108`). Người role USER nhưng được bổ nhiệm khoa vẫn có thể bị chặn tại route.
3. Engine: INITIAL_SIGN cần DOCUMENT_SIGN_DRAFT; approve/reject/request-revision cần DOCUMENT_APPROVE. Theo seed trong repository, PK có SIGN_DRAFT nhưng không có APPROVE; TP có APPROVE nhưng không có SIGN_DRAFT. **Chưa đối chiếu cấu hình thật trên DB đích**, có thể DB đã được cấu hình khác.

Ngoài ra, nhánh approve tự gọi ký nháy chỉ kiểm tra REVIEW_APPROVE, không chạy guard INITIAL_SIGN. `PROBE_3` xác nhận actor bị engine từ chối ký riêng vẫn được approve gọi signer khi có DOCUMENT_APPROVE.

**Cần chốt:** chức vụ/role/permission nào thực sự cho từng hành động; duyệt có mặc nhiên bao gồm quyền ký hay phải có cả hai? Không được giải quyết bằng cách tự thêm quyền cho PK, bỏ role gate hoặc cho admin bypass khi chưa được xác nhận.

### D3 — Tách trách nhiệm thẩm định và phê duyệt cấp sau [NEED_HUMAN]

REVIEW_APPROVE và LEADER_APPROVE hiện cùng yêu cầu DOCUMENT_APPROVE tại cùng đơn vị. Không có guard chức vụ HT/đúng người nhận bước sau hoặc tách người xử lý hai bước. `PROBE_2` xác nhận một actor REVIEWER có permission được cấp trong fixture gọi approve hai lần, khi thiếu PDF, đi từ IN_REVIEW qua PENDING_APPROVAL tới APPROVED.

Đây là bằng chứng kiểm soát hai bước hiện không tự phân biệt cấp duyệt; **không phải kết luận mọi trưởng khoa thực tế đều có thể khai thác**, vì còn role/permission thực tế và hồ sơ/PDF. Nếu yêu cầu Trưởng/Phó khoa chuyển lên Hiệu trưởng được chốt, cần mô hình giao bước và guard tương ứng; không chỉ đổi nhãn currentHolder.

### D4 — Reject và yêu cầu sửa khác nhau ở đâu? [NEED_HUMAN]

Hiện reject → REJECTED, request-revision → NEEDS_REVISION. Cả hai xóa primaryFilePath, bỏ main attachment flag; người soạn được cập nhật và gửi lại từ cả hai trạng thái. Chưa thấy cơ chế reject đóng hồ sơ vĩnh viễn hoặc bắt buộc tạo văn bản mới.

**Cần chốt:** reject là dừng vĩnh viễn, hay vẫn cho sửa/gửi lại? Ai được xem bản PDF đã ký của vòng cũ sau khi đóng băng? Không thay đổi quyền hiện có của người soạn/admin từ các issue trước trong lượt rà này.

## 6. Hướng kiểm thử để AI reviewer xây dựng plan

Không xóa test cũ hoặc nới lỏng assertion. Test mới nên kiểm tra chuỗi service + engine thật, không chỉ mock engine rồi verify rằng executeTransition được gọi.

- Gửi → người nhận ký riêng → duyệt tiếp; chữ ký thẩm định đúng phiên bản không làm luồng bị kẹt hoặc bị coi nhầm chữ ký người soạn.
- Gửi → approve/reject/requestRevision → request được đóng/ghi quyết định đúng vòng; sửa và gửi người khác không còn quyền PENDING cũ ngoài chính sách đã chốt.
- Ký nháy rồi người soạn recall: xác nhận kết quả theo chính sách “bắt đầu xử lý” được chốt, không chỉ theo status PENDING.
- Thiếu PDF, bytes rỗng, storage lỗi, ký lỗi: không được chuyển bước nếu quy định chữ ký bắt buộc; phân biệt lỗi thiếu hồ sơ với lỗi provider.
- Action sai trạng thái: không gọi signer hoặc storage. Tách kiểm tra trạng thái trước ký và kiểm tra lại khi commit.
- Cùng tài khoản đọc detail, workflow logs, timeline và PDF: quyền nhất quán theo khoa/người được giao/độ mật được chốt.
- Role × chức vụ × permission × đơn vị × người được giao: USER/REVIEWER/LEADER/CLERK/ADMIN/SYSTEM_ADMIN; TK/PK/HT; cùng khoa, khoa khác, cấp cha; bổ nhiệm chưa hiệu lực/hết hạn/miễn nhiệm; không suy role từ chức vụ.
- Một người cố duyệt hai cấp, người khác không được giao cố xử lý, quyền đổi sau khi gửi: kết quả phải theo quyết định D1–D3 và được kiểm tra ở service.
- Hai transaction độc lập approve/reject hoặc approve/approve: chỉ một quyết định thắng; kiểm tra trạng thái, log, request, PDF và khả năng retry. Cần PostgreSQL để kiểm chứng hành vi triển khai, không chỉ Mockito.

## 7. Bằng chứng và kết luận bàn giao

| Bằng chứng | Kết quả |
|---|---|
| 6 nhóm test sẵn có | 212 test đạt; không chứng minh các chuỗi chưa được test là đúng |
| PROBE_1 | Không PDF vẫn chuyển PENDING_APPROVAL, không gọi bộ ký |
| PROBE_2 | Cùng actor có DOCUMENT_APPROVE thực hiện được hai bước ở trường hợp không PDF |
| PROBE_3 | Ký riêng bị từ chối, approve vẫn gọi bộ ký với permission APPROVE |
| PROBE_4 | Ký riêng rồi approve bị chặn khi bộ ký báo trùng chữ ký |
| PROBE_5 | Ký/lưu được gọi trước khi từ chối INITIAL_SIGN sai trạng thái |
| PROBE_6 | REVIEWER đọc detail được nhưng route timeline từ chối cùng tài liệu |
| PROBE_7 | Sau ký nháy, request vẫn PENDING và người soạn recall được về DRAFT |

Log test local: `D:/code/e-office/document-review-audit-tests.log`. Harness và log probe: `target/review-audit/WorkflowReviewProbe.java`, `target/review-audit/probe.log` (build artifacts, không phải test đã commit). Source probe dùng mock signer/storage; không coi các dòng PROBE là kiểm thử mật mã hoặc HTTP production. Chưa chạy database thật, test race hay toàn bộ suite.

**Đề nghị AI reviewer:** chấm từng R1–R6 và D1–D4 theo ACCEPT/REJECT/NEED_HUMAN kèm bằng chứng; xác nhận mức ưu tiên và bổ sung test tái hiện. Có thể lập plan riêng cho R1/R2/R4; các thay đổi ma trận quyền, định tuyến, chữ ký bắt buộc và nghĩa của reject phải chờ con người chốt. Không tự sửa nguồn, tạo migration, thay quyền admin hoặc triển khai dựa riêng vào báo cáo này.

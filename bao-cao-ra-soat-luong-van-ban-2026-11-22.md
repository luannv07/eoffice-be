# Báo cáo rà soát luồng văn bản E-office

- Ngày tổng hợp: 22/11/2026.
- Mã nguồn đối chiếu: `NCKH-rework`, commit `915077c` — `feat: add position draft-sign permission endpoint`.
- Phạm vi: từ tạo dự thảo, chọn đơn vị/người nhận, gửi, thẩm định, trả sửa, gửi lại, phê duyệt, cấp số, phát hành, phân phối đến thu hồi.
- Căn cứ: `AGENTS.md`, `GEMINI.md`, `docs/permission-matrix.md`, `docs/lessons.md`, mã nguồn, migration và test hiện có.
- Phương pháp: review tĩnh, lần theo đường gọi giữa controller, service, policy, workflow engine và repository.
- Giới hạn: chưa chạy test/build, chưa tái hiện qua API, chưa đối chiếu database hoặc cấu hình đang sử dụng. Không coi các kết quả test cũ trong tài liệu là kết quả kiểm chứng của lượt review này.
- Báo cáo này thay danh sách đã trình bày trước đó trong cuộc trao đổi; không thay đổi ma trận nghiệp vụ.

## 1. Cách đọc báo cáo

- **Xác nhận qua code:** điều kiện hoặc sự không nhất quán đã được xác định từ mã nguồn; chưa đồng nghĩa đã tái hiện trên môi trường vận hành.
- **Phụ thuộc cấu hình/seed:** tác động phụ thuộc permission, role, phân nhiệm và dữ liệu thực tế; cần kiểm tra database đích.
- **NEED_HUMAN:** cần người phụ trách chốt nghiệp vụ; không tự suy diễn chính sách để sửa.
- **Thiếu test:** chưa tìm thấy ca kiểm thử trực tiếp cho tình huống được nêu.
- **Rủi ro cần tái hiện:** có cơ sở từ code nhưng chưa kết luận hành vi cạnh tranh hoặc dữ liệu thực tế đã xảy ra.

Mức độ cao/trung được dùng cho lỗi hoặc rủi ro. Hành vi chặn đúng theo quy tắc hiện tại không được tính thành lỗi phân quyền chỉ vì làm một tuyến chưa được hỗ trợ dừng lại.

## 2. Kết luận về ngành, khoa/phòng và ký nháy

Ngành là loại đơn vị `MAJOR`; `CV/GV/TK/PK/TP/PP/HT/PHT/VT` là chức vụ; `USER/REVIEWER/LEADER/CLERK/ADMIN/SYSTEM_ADMIN` là role hệ thống. Ba khái niệm này không đồng nhất.

**Hai tài khoản cùng ngành vẫn có thể không gửi văn bản lên được.** Nếu người gửi là `USER` có chức vụ `CV/GV` và phân nhiệm tại `MAJOR`, code chặn ngay khi xác định tuyến người gửi, trước khi tìm người nhận. Thêm người nhận vào cùng ngành hoặc cấp thêm permission ký không mở được tuyến đó.

Không thể khẳng định toàn bộ luồng chỉ có khoa/phòng: HT/PHT có tuyến cấp trường và văn thư có tuyến riêng. Tuy nhiên, tuyến USER thông thường hiện chỉ được quy định cho khoa/phòng.

**Không chỉ TK/TP mới có thể ký nháy.** Thao tác thẩm định phụ thuộc người được giao hiện hành, tài khoản hoạt động, permission `DOCUMENT_SIGN_DRAFT`, phạm vi đơn vị và role gate tại API. Endpoint gán permission mới cũng không khóa cứng chỉ TK/TP.

### 2.1. Permission mặc định trong seed

Đây là dữ liệu migration trong repository, chưa phải kết quả kiểm kê database đang chạy.

| Chức vụ | `DOCUMENT_SIGN_DRAFT` | `DOCUMENT_APPROVE` | `DOCUMENT_SIGN_OFFICIAL` | Hệ quả đáng chú ý |
| --- | --- | --- | --- | --- |
| HT | Không | Có | Có | Duyệt lãnh đạo và ký phát hành; không có quyền ký nháy thẩm định mặc định |
| PHT | Có | Có | Không | Có permission ký nháy nhưng không phải người nhận thẩm định thông thường của tuyến đầu |
| TK | Có | Có | Không | Đủ hai permission xử lý thẩm định |
| PK | Có | Không | Không | Có thể ký nhưng thiếu quyền quyết định chuyển tiếp/trả sửa/từ chối |
| TP | Không | Có | Không | Thiếu ký nháy bắt buộc trước chuyển tiếp |
| PP | Không | Không | Không | Thiếu cả hai permission thẩm định |
| VT | Không | Không | Không | Bước cấp số có nhánh tự ký riêng, không qua quyền INITIAL_SIGN của engine |

Nguồn: [seed position_permissions](/D:/code/e-office/NCKH-rework/src/main/resources/db/changelog/changes/2026-08-10_seed-dictionary-and-permissions.yml:95), [PositionPermissionService.java](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/PositionPermissionService.java:43).

### 2.2. Đối chiếu theo role

| Role | Tạo/gửi hồ sơ mình | Thẩm định/ký nháy | Duyệt lãnh đạo | Cấp số | Ký phát hành |
| --- | --- | --- | --- | --- | --- |
| USER | Theo đơn vị/phân nhiệm hợp lệ | Controller chặn, dù routing có thể chọn người có chức vụ phù hợp | Controller chặn | Không theo role | Không |
| REVIEWER | Theo phân nhiệm hợp lệ | Đúng người nhận + permission + phạm vi | Qua role gate nhưng thiếu guard giao việc đầy đủ tại PENDING_APPROVAL | Không theo role | Không |
| LEADER | Theo chức vụ thực tế | API cho phép nếu đủ điều kiện; handover-options lại thiếu role này | Theo permission; chưa ràng buộc đầy đủ người được giao | Không theo role | Cần DOCUMENT_SIGN_OFFICIAL và các điều kiện nghiệp vụ |
| CLERK | Có tuyến HT/PHT, kể cả không phân nhiệm | Không qua role gate thẩm định | Không qua role gate | Có nhánh cho phép | API chuẩn chặn role này |
| ADMIN | Chỉ hồ sơ mình; mô phỏng theo cấu hình dev/test | Cấm tại service | Cấm tại service | Cấm tại service | Cấm tại service |
| SYSTEM_ADMIN | Như ADMIN | Cấm tại service | Cấm tại service | Cấm tại service | Cấm tại service |

Admin được đọc toàn bộ theo quy tắc đã chốt, nhưng quyền đọc không cấp quyền xử lý nghiệp vụ. Bảng này không tự cấp quyền dựa trên role; phân nhiệm, permission, trạng thái và giao việc vẫn là điều kiện riêng.

## 3. Các điểm liên quan trực tiếp đến ngành và chức vụ

### 01 — USER thuộc ngành MAJOR bị chặn dù người nhận cùng ngành

**Phân loại:** nghẽn nghiệp vụ theo giới hạn hiện tại — NEED_HUMAN; chưa coi là lỗi phân quyền.

**Tình huống:** người soạn role USER, chức vụ CV/GV, phân nhiệm tại ngành A; người nhận cũng thuộc ngành A.

| Loại đơn vị của người gửi | Tuyến hiện tại |
| --- | --- |
| FACULTY — Khoa | TK/PK đúng khoa |
| DEPARTMENT — Phòng | TP/PP đúng phòng |
| MAJOR — Ngành/Tổ | Không có tuyến; bị chặn |
| MAJOR có khoa cha | Không tự tìm trưởng/phó khoa cấp trên |

**Nguyên nhân:** `DraftUnitResolver.identity()` chỉ mở tuyến USER thông thường cho FACULTY hoặc DEPARTMENT. MAJOR rơi vào lỗi “Đơn vị của USER không phải khoa/phòng hợp lệ”. Điều kiện chạy trước bước tìm người nhận; cùng ID ngành không làm loại đơn vị trở thành hợp lệ.

Resolver được dùng tại options/tạo/sửa dự thảo và tuyến gửi lần đầu. Vì vậy có thể bị chặn trước cả bước gửi; với dự thảo đã tồn tại, bước gửi DRAFT vẫn kiểm tra lại tuyến.

**Tác động:** hai tài khoản cùng ngành không tạo thành một tuyến văn bản được hỗ trợ. Thêm permission DOCUMENT_SIGN_DRAFT không thay đổi điều kiện tuyến.

**Hướng xử lý:** chốt ngành có được soạn/gửi không; nếu có, gửi cho chức vụ nào, trong ngành hay lên khoa/phòng nào. Ma trận hiện tại chưa định nghĩa tuyến này, không tự mở rộng lên đơn vị tổ tiên.

**Bằng chứng:** [DraftUnitResolver.java:55](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DraftUnitResolver.java:55), [permission-matrix.md:55](/D:/code/e-office/docs/permission-matrix.md:55).

### 02 — Một số chức danh bị chặn dù hai tài khoản cùng đơn vị

**Phân loại:** giới hạn nghiệp vụ đã ghi trong ma trận; cần chốt thêm nếu muốn hỗ trợ.

**Tình huống:** người gửi có phân nhiệm GĐTT/GDTT/PGDTT/BT/PBT/UV, kể cả cùng ngành/khoa/phòng với người nhận.

**Nguyên nhân:** resolver có danh sách UNSUPPORTED và từ chối trước khi xét tuyến thông thường, báo “Chức danh chưa có tuyến gửi duyệt được quy định trên hệ thống”.

**Tác động:** cùng đơn vị hoặc bổ sung permission ký không giải quyết được việc chưa có tuyến theo chức danh.

**Hướng xử lý:** định nghĩa tuyến cho từng chức danh nếu nghiệp vụ yêu cầu; không tự coi các chức danh này là USER thông thường.

**Bằng chứng:** [DraftUnitResolver.java:23](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DraftUnitResolver.java:23).

### 03 — TRUNG: Chức vụ khoa/phòng gán vào ngành lại có thể lọt tuyến

**Phân loại:** xác nhận qua code; tác động phụ thuộc cấu hình phân nhiệm.

**Nguyên nhân:** API phân nhiệm kiểm tra mã chức vụ và đơn vị tồn tại nhưng không ràng buộc cặp TK/PK–FACULTY, TP/PP–DEPARTMENT hoặc HT/PHT–đơn vị cấp trường. Resolver nhận diện TK/PK/TP/PP rồi mở tuyến HT/PHT trước bước kiểm tra FACULTY/DEPARTMENT.

**Tác động:** cấu hình TK tại MAJOR có thể đi tuyến lãnh đạo, trong khi USER/CV/GV tại cùng ngành bị chặn. Đây là trường hợp khác với mục 01.

**Hướng xử lý:** chốt, kiểm tra cặp chức vụ–loại đơn vị và kiểm kê phân nhiệm sai; không dùng việc gán nhầm chức vụ để mở tuyến ngành.

**Bằng chứng:** [PositionAssignmentServiceImpl.java:71](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/PositionAssignmentServiceImpl.java:71), [DraftUnitResolver.java:45](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DraftUnitResolver.java:45).

### 04 — TRUNG: Thiếu test trực tiếp cho tình huống ngành

**Phân loại:** khoảng trống kiểm thử, chưa chạy bổ sung.

Chưa tìm thấy test trực tiếp cho hai tài khoản cùng MAJOR gửi văn bản, USER ngành trình lên khoa cha, cấp permission ký nhưng đơn vị vẫn là MAJOR, hoặc so sánh USER/CV/GV với TK/TP gán tại cùng ngành.

Test hiện có kiểm tra tuyến khoa/phòng và chức danh chưa được hỗ trợ. Không được mô tả tình huống người dùng đã gặp là “đã test tái hiện”.

**Hướng xử lý:** sau khi chốt tuyến ngành, thêm test service/API cho cả người hợp lệ và không hợp lệ, giữ kiểm tra không ký/lưu khi bị chặn.

**Bằng chứng:** [DraftUnitResolverTest.java:34](/D:/code/e-office/NCKH-rework/src/test/java/vn/edu/epu/eoffice_be/service/DraftUnitResolverTest.java:34), [SubmissionRoutingServiceTest.java:165](/D:/code/e-office/NCKH-rework/src/test/java/vn/edu/epu/eoffice_be/service/SubmissionRoutingServiceTest.java:165).

## 4. Các lỗi và chênh lệch còn lại theo vòng đời

### 05 — CAO: PK/TP/PP có thể nhận hồ sơ nhưng không hoàn tất xử lý

**Phân loại:** xác nhận chênh lệch routing–seed; cần kiểm tra permission database thật.

Routing cho chọn TK/PK/TP/PP nhưng không kiểm tra đủ permission hoàn thành thẩm định. Theo seed, PK ký được nhưng thiếu DOCUMENT_APPROVE; TP có DOCUMENT_APPROVE nhưng thiếu chữ ký nháy bắt buộc; PP thiếu cả hai.

**Tác động:** gửi thành công nhưng người nhận không chuyển tiếp hoặc ra quyết định được.

**Hướng xử lý:** thống nhất quyền với ma trận và kiểm tra khả năng xử lý trước khi giao. Không tự suy ra cần cấp toàn bộ quyền cho mọi chức vụ nếu chưa được xác nhận.

**Bằng chứng:** [SubmissionRoutingService.java:135](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/SubmissionRoutingService.java:135), [seed permission:95](/D:/code/e-office/NCKH-rework/src/main/resources/db/changelog/changes/2026-08-10_seed-dictionary-and-permissions.yml:95).

### 06 — CAO: Chức vụ và permission đúng nhưng role khiến người nhận bị chặn

**Phân loại:** xác nhận qua code; phụ thuộc role/phân nhiệm thực tế.

Routing có thể chọn USER được bổ nhiệm TK, nhưng controller ký/duyệt không cho USER thực hiện. API phân nhiệm không tự đồng bộ role. Ngược lại, LEADER được gọi ký/duyệt nhưng handover-options không có role LEADER.

**Tác động:** người đã được giao không xử lý hoặc không lấy được lựa chọn lãnh đạo khi cần chuyển tiếp.

**Hướng xử lý:** thống nhất điều kiện đủ tư cách giữa routing, API lựa chọn và API xử lý; tiếp tục đối chiếu role database, permission và giao việc.

**Bằng chứng:** [DocumentWorkflowController.java:68](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/controller/DocumentWorkflowController.java:68), [SubmissionRoutingService.java:135](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/SubmissionRoutingService.java:135).

### 07 — CAO: Văn thư không phân nhiệm gửi được nhưng lãnh đạo duyệt bị chặn

**Phân loại:** xác nhận mâu thuẫn giữa các bước trong code.

Chuỗi: CLERK không phân nhiệm → hồ sơ không đơn vị → gửi HT/PHT → PENDING_APPROVAL → duyệt bị từ chối. Resolver cho phép tuyến này nhưng engine yêu cầu document.getOrgId() khác null cho hành động duyệt.

**Tác động:** tuyến hợp lệ ở đầu vào không qua được bước lãnh đạo.

**Hướng xử lý:** thống nhất phạm vi quyền cho tuyến được phép không có đơn vị; không tự gán đơn vị để né lỗi.

**Bằng chứng:** [DraftUnitResolver.java:37](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DraftUnitResolver.java:37), [WorkflowEngine.java:161](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/workflow/WorkflowEngine.java:161).

### 08 — CAO: Gửi lại sau trả sửa không dùng tuyến gửi lần đầu

**Phân loại:** xác nhận qua code; NEED_HUMAN về đích gửi lại.

Từ NEEDS_REVISION, code dùng policy cũ, mở phạm vi lên đơn vị tổ tiên và luôn đặt IN_REVIEW, kể cả người nhận là HT/PHT. Gửi lại cho HT có thể buộc HT ký nháy thẩm định, trong khi HT thiếu permission này mặc định. Danh sách gửi lại có fallback admin nhưng service lại không chấp nhận admin làm người duyệt.

**Tác động:** sai cấp xử lý, lựa chọn người nhận không nhất quán và có thể kẹt khi ký.

**Hướng xử lý:** chốt gửi lại cho ai/quay lại cấp nào, rồi đồng bộ options và submit.

**Bằng chứng:** [DocumentApprovalService.java:249](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DocumentApprovalService.java:249).

### 09 — CAO: Cấp lãnh đạo chưa kiểm tra đầy đủ người được giao

**Phân loại:** xác nhận guard hiện chỉ áp dụng IN_REVIEW; NEED_HUMAN về quyền xử lý cấp lãnh đạo.

Ở PENDING_APPROVAL, người qua role gate và có DOCUMENT_APPROVE đúng phạm vi có thể gọi API xử lý dù request giao cho người khác. Guard chống gọi duyệt lặp sau bàn giao chỉ xử lý một trường hợp hẹp.

**Tác động:** giao việc đích danh chưa được bảo vệ tương đương cấp thẩm định.

**Hướng xử lý:** chốt quyền duyệt thay, tự duyệt và ủy quyền; áp guard phù hợp trước signer/storage và quyết định.

**Bằng chứng:** [ReviewAssignmentPolicy.java:29](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/ReviewAssignmentPolicy.java:29), [WorkflowEngine.java:174](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/workflow/WorkflowEngine.java:174).

### 10 — TRUNG: Lãnh đạo xử lý xong nhưng request có thể vẫn PENDING

**Phân loại:** xác nhận qua code.

Nhánh duyệt lãnh đạo không hoàn tất request như nhánh thẩm định. Trả sửa/từ chối tại PENDING_APPROVAL cũng không nhận request từ guard để đóng.

**Tác động:** Document đã APPROVED/NEEDS_REVISION/REJECTED nhưng yêu cầu giao việc vẫn PENDING, khiến lịch sử và trạng thái xử lý không đồng nhất.

**Hướng xử lý:** cập nhật kết quả, thời điểm, lý do request trong cùng transaction chuyển trạng thái.

**Bằng chứng:** [DocumentWorkflowServiceImpl.java:213](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:213).

### 11 — TRUNG: Văn thư thấy hàng chờ nhưng bị chặn một số dữ liệu cần xử lý

**Phân loại:** xác nhận policy đọc không nhất quán.

CLERK được xem pending-publish nhưng policy workflow có thể chặn lịch sử và kiểm định chữ ký hồ sơ APPROVED nếu không phải người soạn/người ký. API chi tiết chính dùng handler khác với policy khác.

**Tác động:** thấy hồ sơ cần xử lý nhưng không đọc được đủ bằng chứng để kiểm tra.

**Hướng xử lý:** thống nhất quyền đọc chi tiết, PDF, lịch sử và kiểm định cho người xử lý hàng chờ.

**Bằng chứng:** [DocumentWorkflowServiceImpl.java:694](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:694), [WebMvcMappingConfig.java:63](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/config/WebMvcMappingConfig.java:63).

### 12 — CAO: Cấp số và ký phát hành chưa thống nhất người thực hiện

**Phân loại:** xác nhận chênh lệch giữa các nhánh; NEED_HUMAN về người ký phát hành.

API ký/phát hành chuẩn chặn CLERK; permission phát hành mặc định thuộc HT. Nếu HT gọi phát hành kèm bookId để cấp số, service cấp số lại yêu cầu quyền văn thư.

**Tác động:** nhánh phát hành kèm cấp số không phù hợp quyền mặc định. Chuỗi văn thư cấp số trước rồi HT ký có thể đi được, chưa đồng nghĩa các nhánh khác hoạt động.

**Hướng xử lý:** chốt ai ký phát hành và cấp số có bắt buộc là bước riêng không; đồng bộ controller, service và permission.

**Bằng chứng:** [DocumentWorkflowController.java:105](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/controller/DocumentWorkflowController.java:105), [DocumentWorkflowServiceImpl.java:489](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:489).

### 13 — CAO: Văn thư tự soạn rồi tự cấp số có thể gặp lỗi ký trùng

**Phân loại:** xác định xung đột field chữ ký qua code; chưa tái hiện bằng PDF thật trong lượt này.

Người soạn đã ký khi gửi bằng sig_user_<id>. Bước cấp số tiếp tục ký cùng field cho cùng người; signer từ chối trùng và service cấp số chuyển thành lỗi, transaction rollback.

**Tác động:** chính văn thư soạn không cấp số được; văn thư khác có thể xử lý nhưng chưa có quy tắc tự xử lý rõ ràng.

**Hướng xử lý:** chốt có cho tự cấp số không và xử lý đúng mục đích chữ ký; không bỏ guard chống ký trùng.

**Bằng chứng:** [DocumentBookServiceImpl.java:313](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentBookServiceImpl.java:313), [PdfSigner.java:152](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/security/pki/PdfSigner.java:152).

### 14 — CAO: Hai API phát hành không nối thành một hợp đồng hoàn chỉnh

**Phân loại:** xác nhận qua code.

API cũ /publish yêu cầu APPROVED và đọc officialFilePath. API chuẩn /workflow/sign-and-publish tạo PDF chính thức nhưng đồng thời chuyển PUBLISHED. Hồ sơ mới gọi API cũ trước thì thiếu PDF; gọi API chuẩn trước rồi API cũ thì sai trạng thái.

Với dữ liệu cũ có officialFilePath ở APPROVED, API cũ tính hash rồi phát hành mà không dùng đầy đủ guard ký/phạm vi và ký niêm phong của engine.

**Tác động:** nhánh cũ bị kẹt với hồ sơ bình thường và có kiểm soát khác với nhánh chuẩn.

**Hướng xử lý:** thống nhất hợp đồng phát hành và xử lý caller của endpoint cũ.

**Bằng chứng:** [DocumentPublishService.java:85](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DocumentPublishService.java:85).

### 15 — CAO: Phát hành thành công nhưng chưa phân phối tới người nhận

**Phân loại:** xác nhận thiếu bước phân phối trong đường phát hành chuẩn.

API chuẩn không tạo DocumentDistribution và không nhận danh sách đơn vị phát hành. Danh sách phát hành của người dùng thông thường lại truy vấn qua bảng phân phối. API cũ có phân phối nhưng vướng hợp đồng ở mục 14.

**Tác động:** hồ sơ có thể đã PUBLISHED, có PDF chính thức nhưng người nhận không thấy trong danh sách.

**Hướng xử lý:** đưa phân phối vào luồng phát hành chuẩn theo phạm vi được chốt.

**Bằng chứng:** [DocumentWorkflowServiceImpl.java:595](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:595), [DocumentRepository.java:235](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/repository/DocumentRepository.java:235).

### 16 — CAO: Quyền đọc qua ID/URL trực tiếp khác quyền đọc danh sách

**Phân loại:** xác nhận policy đọc khác nhau; cần chốt đầy đủ phạm vi đọc sau duyệt/phát hành.

Danh sách phát hành có lọc phân phối, nhưng đường tải PDF có nhánh cho đọc theo APPROVED/PUBLISHED, không đối chiếu phân phối hoặc độ mật. Handler chi tiết chính cũng thiếu kiểm tra đối tượng tương ứng ngoài nhóm trạng thái dự thảo được bảo vệ.

**Tác động:** người không thấy hồ sơ trong danh sách vẫn có thể đọc bằng ID/URL trực tiếp.

**Hướng xử lý:** áp một policy nhất quán cho danh sách, chi tiết, PDF, lịch sử và kiểm định theo quyết định nghiệp vụ. Không coi bộ lọc danh sách là hàng rào bảo mật.

**Bằng chứng:** [DocumentAttachmentService.java:196](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DocumentAttachmentService.java:196), [DocumentDraftService.java:265](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/DocumentDraftService.java:265).

### 17 — CAO: Tài khoản bị khóa hoặc mất role chưa được chặn đồng đều

**Phân loại:** xác nhận thiếu kiểm tra hiện trạng tại một số đường xử lý.

Nhánh IN_REVIEW kiểm tra active qua policy. getCurrentActor() của workflow chỉ tìm tài khoản chưa xóa; các nhánh lãnh đạo/phát hành/thu hồi thiếu guard active tương đương. Service cấp số còn chấp nhận ROLE_CLERK trong authentication dù role database đã đổi; JWT filter dựng authority từ role token.

**Tác động:** tài khoản bị khóa hoặc mất role chưa được bảo đảm mất quyền tại mọi service nghiệp vụ khi token còn hiệu lực.

**Hướng xử lý:** kiểm tra tài khoản hoạt động, role và permission hiện tại từ database trước tác dụng phụ.

**Bằng chứng:** [DocumentWorkflowServiceImpl.java:731](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:731), [DocumentBookServiceImpl.java:385](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentBookServiceImpl.java:385), [JwtAuthenticationFilter.java](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/security/JwtAuthenticationFilter.java).

### 18 — CAO: Thu hồi một văn bản có thể ảnh hưởng chữ ký trên văn bản khác

**Phân loại:** xác nhận hành vi thu hồi serial qua code; phạm vi ảnh hưởng cần đối chiếu dữ liệu chứng thư.

Sau khi đổi trạng thái thu hồi hồ sơ, code đọc mọi chữ ký trong PDF và thu hồi serial chứng thư tương ứng. Chứng thư người dùng được tái sử dụng nên thu hồi A có thể khiến chữ ký cùng chứng thư trên B bị kiểm định là đã thu hồi. Ghi danh sách thu hồi không cùng transaction database.

**Tác động:** thu hồi hồ sơ gây tác động vượt ra ngoài chính hồ sơ đó.

**Hướng xử lý:** tách thu hồi văn bản khỏi thu hồi chứng thư; chỉ thu hồi chứng thư theo nghiệp vụ riêng.

**Bằng chứng:** [DocumentWorkflowServiceImpl.java:1336](/D:/code/e-office/NCKH-rework/src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java:1336).

## 5. Rủi ro cần tái hiện riêng

Không tính các mục dưới đây là đã kiểm chứng bằng chạy thử trong lượt review.

| Mã | Mức | Tình huống | Cơ sở và việc cần kiểm chứng |
| --- | --- | --- | --- |
| R01 | Cao | Hai văn thư cấp số cùng hồ sơ từ hai sổ | Khóa từng sổ chưa bảo vệ chung document; dùng hai transaction PostgreSQL kiểm tra số, liên kết sổ, bộ đếm, PDF và audit sau cạnh tranh |
| R02 | Cao | Phát hành đồng thời với phát hành/trả sửa | signAndPublish đọc document không khóa như các thao tác thẩm định; kiểm tra trạng thái, số, PDF, rollback và tác dụng phụ |
| R03 | Trung | Hồ sơ đang PENDING_SIGN/ISSUED | Có enum/API đọc nhưng engine thiếu tuyến xử lý tương ứng; kiểm kê xem database thực tế có hồ sơ ở các trạng thái này không |

## 6. Kiểm thử cần có trước khi nghiệm thu toàn luồng

Các ca dưới đây là đề xuất kiểm chứng, chưa phải kết quả chạy.

| Nhóm | Ca cần kiểm tra | Điều cần đối chiếu |
| --- | --- | --- |
| Ngành | Hai tài khoản cùng MAJOR; MAJOR có khoa cha; cấp permission ký nhưng vẫn MAJOR | Theo quyết định tuyến; bị chặn phải có thông báo đúng và không ký/lưu |
| Chức danh | Từng chức danh chưa có tuyến; TK/TP gán tại MAJOR | Không dùng cấu hình sai để vượt chính sách |
| Role/permission | TK/PK/TP/PP × USER/REVIEWER/LEADER × SIGN_DRAFT/APPROVE | Người được giao đủ quyền thực hiện hết bước; người sai quyền bị từ chối |
| Tuyến thẳng | CLERK không phân nhiệm → HT/PHT | Không kẹt vì đơn vị trống được phép |
| Thẩm định | Ký nháy → chuyển tiếp trên cùng hồ sơ/PDF | Đúng request, hash, người nhận lãnh đạo và quyền đọc sau bàn giao |
| Gửi lại | Trả sửa từ thẩm định và từ lãnh đạo → main mới → gửi lại | Đúng cấp/vòng theo quy tắc được chốt, không dùng PDF cũ |
| Lãnh đạo | Người được giao/người khác/người soạn/ủy quyền gọi API trực tiếp | Đúng quyền xử lý; đóng request cùng transaction |
| Văn thư | Người soạn tự cấp số và văn thư khác cấp số | Không ký trùng hoặc xử lý theo quy tắc tự xử lý được chốt |
| Phát hành | Cấp số → ký → PDF chính thức → phân phối | Cùng hồ sơ đi hết luồng; số, hash, audit và nơi nhận nhất quán |
| Đọc | Người nhận/người ngoài phạm vi qua danh sách, chi tiết, PDF, lịch sử và kiểm định | Cùng policy, kể cả độ mật và URL truy cập trực tiếp |
| Tài khoản | Khóa hoặc đổi role sau khi cấp JWT | Mọi service đọc đúng hiện trạng database |
| Thu hồi | Hai văn bản dùng cùng chứng thư, chỉ thu hồi một | Không làm ảnh hưởng chữ ký hồ sơ khác do thu hồi văn bản |
| Cạnh tranh | Gửi/gửi; duyệt/thu hồi; cấp số/cấp số; phát hành/trả sửa | Trạng thái, request, số, tệp, log và event không bị lặp/ghi đè |

Test hiện có cho văn thư ký phát hành trong DocumentWorkflowServiceTest mock WorkflowEngine và gọi service trực tiếp. Test đó không chứng minh CLERK qua được role gate controller và permission resolver thật.

Nguồn: [DocumentWorkflowServiceTest.java:978](/D:/code/e-office/NCKH-rework/src/test/java/vn/edu/epu/eoffice_be/service/DocumentWorkflowServiceTest.java:978).

## 7. Những lỗi cũ không tính lại thành tồn đọng

Qua đối chiếu code hiện tại, các điểm sau đã có thay đổi xử lý, không lặp lại thành lỗi chưa sửa trong danh sách này:

- REJECTED chặn sửa/upload/restore/gửi lại và giữ hồ sơ chỉ đọc.
- Bàn giao thẩm định tạo request lãnh đạo và hoàn tất request thẩm định.
- Duyệt chuyển tiếp đối chiếu chữ ký nháy theo request/hash thay vì tự ký lại.
- Getter danh sách đơn vị phân phối trong API cũ đã đọc trực tiếp recipientOrgIds.
- Các thao tác gửi, thu hồi và thẩm định đã bổ sung khóa; không suy rộng rằng mọi thao tác cấp số/phát hành cũng được bảo vệ tương đương.

Đây là nhận xét theo code, không phải xác nhận nghiệm thu lại bằng test của lượt này.

## 8. Quyết định nghiệp vụ còn cần con người chốt

| Vấn đề | Nội dung cần chốt |
| --- | --- |
| Ngành và các đơn vị khác | Có tham gia soạn/gửi không; người nhận trong ngành hay khoa/phòng cấp trên; phạm vi ký và duyệt |
| Chức danh chưa có tuyến | Tuyến cụ thể của GĐTT/GDTT/PGDTT/BT/PBT/UV |
| Quyền thẩm định | PK/PP có được duyệt chuyển tiếp, yêu cầu sửa, từ chối không; bộ permission của từng chức vụ |
| Cấp lãnh đạo | Đúng người nhận, duyệt thay, tự duyệt và ủy quyền |
| Vòng sửa | Gửi lại người yêu cầu sửa hay chọn người khác; quay lại cấp nào; xử lý khi người nhận hết quyền/nghỉ việc |
| Văn thư và phát hành | Ai ký phát hành; cấp số là bước riêng hay gộp; có cho tự cấp số; cơ chế nhận/chuyển việc |
| Phạm vi đọc | Quyền sau APPROVED/PUBLISHED, phân phối, độ mật và truy cập trực tiếp |

## 9. Trạng thái bàn giao

- Đã làm: lập báo cáo 18 mục, tách riêng tình huống hai tài khoản cùng ngành, phân biệt hành vi theo quy tắc, chênh lệch code/cấu hình và khoảng trống kiểm thử.
- Thay đổi: chỉ tạo file báo cáo này theo yêu cầu; không sửa production, test, rule, ma trận quyền, lessons hoặc pom.xml; không commit/push.
- Kiểm tra nghiệp vụ: review tĩnh; chưa chạy test/build hoặc API; chưa kiểm kê database thật.
- Chưa thể gán chính xác nguyên nhân cho lần người dùng test vì chưa có tài khoản, phân nhiệm, trạng thái hồ sơ và thông báo lỗi của lần đó. Nếu người gửi là USER/CV/GV tại MAJOR, mục 01 giải thích nhánh chặn; chức vụ khác phải xét nhánh tương ứng.
- Việc còn lại: chốt các mục NEED_HUMAN, xác minh cấu hình đích và kiểm thử các chuỗi đã liệt kê trước khi kết luận luồng hoạt động đầy đủ.

VERDICT: REVISE

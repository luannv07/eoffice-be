# Các Luồng Nghiệp Vụ Cơ Bản Trong Hệ Thống E-Office

Tài liệu trình bày chi tiết, toàn diện và chính xác toàn bộ các luồng nghiệp vụ trong hệ thống eoffice-be theo đúng thiết kế và mã nguồn thực tế, sử dụng định dạng văn bản thuần (không bảng biểu, không sơ đồ hình vẽ).

---

## 1. Các Vai Trò Tham Gia Trong Hệ Thống

- Người soạn thảo (Chuyên viên): Tạo dự thảo văn bản, đính kèm tệp tin tài liệu, quản lý phiên bản, gửi trình duyệt, thu hồi yêu cầu duyệt hoặc tiếp thu chỉnh sửa nếu bị trả về.
- Người thẩm định (Trưởng phòng / Chuyên viên thẩm định): Kiểm tra thể thức và nội dung, thực hiện ký nháy điện tử hoặc trả về yêu cầu chỉnh sửa.
- Lãnh đạo (Ban Giám hiệu / Trưởng đơn vị): Xem xét nội dung, ghi ý kiến chỉ đạo (bút phê điều hành), phê duyệt chính thức hoặc từ chối văn bản.
- Cán bộ văn thư: Quản lý sổ văn bản, tiếp nhận văn bản đã được Lãnh đạo duyệt để cấp số văn bản chính thức và thực hiện phát hành, phân phối văn bản.
- Đơn vị / Người nhận: Nhận văn bản theo phân phối, tra cứu, đọc văn bản và tải tài liệu PDF chính thức có chữ ký số.
- Quản trị viên (Admin / System Admin): Quản lý người dùng, tài khoản, cơ cấu cây tổ chức, phân quyền chức vụ, danh mục dùng chung và theo dõi nhật ký kiểm toán.

---

## 2. Quản Lý Tệp Tin Đính Kèm (Lưu Trữ Tại Đâu và Cơ Chế Hoạt Động)

Trong hệ thống, tệp tin đính kèm được quản lý thông qua dịch vụ lưu trữ FileStorageService và DocumentAttachmentService với các quy tắc sau:

- Vị trí lưu trữ vật lý:
  + Tệp tin được lưu trên ổ đĩa máy chủ tại thư mục cấu hình qua biến eoffice.storage.base-path trong file application.yml (mặc định là thư mục ./uploads tại thư mục gốc của dự án).
  + Tệp đính kèm dự thảo và tệp sau khi đóng dấu ký nháy được phân loại vào thư mục con, ví dụ: ./uploads/drafts/.
  + Bản PDF chính thức sau khi phát hành được lưu đường dẫn vào trường official_file_path của văn bản.

- Cơ chế phân loại tệp đính kèm:
  + Tệp văn bản chính (isMain = true): Là tệp nội dung chính thức của văn bản (DOCX hoặc PDF).
  + Tệp phụ lục đính kèm (isMain = false): Là các tài liệu tham khảo, bảng biểu, danh sách kèm theo.
  + Chuyên viên có thể tải lên nhiều tệp đính kèm, tải về đọc hoặc xóa bớt tệp phụ lục thừa khi văn bản còn ở trạng thái nháp.

- Quy tắc đặt tên và chống trùng lặp:
  + Tên tệp tin vật lý không lưu theo tên gốc của người dùng tải lên, mà luôn được sinh tiền tố ngẫu nhiên UUID dạng: {UUID}_{tên_tệp_an_toàn}.
  + Ví dụ: a1b2c3d4-e5f6-7890-abcd-1234567890ab_ke_hoach_tuyen_sinh.pdf.
  + Cơ chế này giúp đảm bảo không bao giờ bị ghi đè tệp tin khi hai người tải lên tệp có cùng tên, đồng thời chống lộ thông tin cấu trúc thư mục.

- Giới hạn và an toàn lưu trữ:
  + Giới hạn kích thước tệp tải lên an toàn tối đa từ 20MB đến 25MB (cấu hình qua eoffice.storage.max-file-size-bytes).
  + Hệ thống kiểm tra chống tấn công Path Traversal: đường dẫn tệp luôn được chuẩn hóa và kiểm tra nghiêm ngặt phải nằm trọn vẹn bên trong thư mục rootStoragePath. Nếu phát hiện tệp hoặc thư mục là liên kết mềm (Symbolic Link) trỏ ra bên ngoài, hệ thống sẽ chặn và báo lỗi ngay lập tức.
  + Hệ thống có thêm module FileStorageController hỗ trợ nạp mở rộng (hiện đã tích hợp cơ chế lưu trữ cục bộ và lưu trữ qua Telegram Provider).

---

## 3. Luồng Sổ Văn Bản và Cấp Số Văn Bản (Cơ Chế Khởi Tạo, Cấp Số và Reset Đầu Năm)

Sổ văn bản (DocumentBook) trong hệ thống được quản lý 2 cấp: Cấp 1 (Đơn vị/Trường) và Cấp 2 (Phòng/Khoa/Ban).

- Bước 1: Khởi tạo sổ văn bản và thiết lập mẫu số
  + Đầu mỗi năm, cán bộ văn thư tạo các sổ văn bản mới cho từng đơn vị và từng loại văn bản (Quyết định, Công văn, Thông báo,...).
  + Mỗi cuốn sổ gắn chặt với 3 thông tin định danh: Năm (year), Đơn vị sở hữu (org_node_id), và Loại văn bản (document_type_id).
  + Hệ thống ràng buộc mỗi đơn vị trong một năm chỉ được tồn tại duy nhất một sổ cho một loại văn bản (không thể tạo trùng).
  + Mẫu số văn bản (numberPattern) bắt buộc phải chứa chuỗi {NUM}, ví dụ: {NUM}/QĐ-ĐHEPU hoặc {NUM}/TB-CNTT.
  + Sổ mới tạo luôn có số hiện tại bằng 0 (currentNumber = 0) và trạng thái mở (isClosed = false).

- Bước 2: Cơ chế reset số thứ tự khi sang năm mới
  + Khi bước sang năm mới (ví dụ từ năm 2025 sang 2026), số thứ tự của văn bản được reset tự động bằng cách tạo cuốn sổ mới cho năm 2026.
  + Vì mỗi sổ thuộc về một năm riêng biệt (trường book_year trong cơ sở dữ liệu), sổ năm 2026 bắt đầu với currentNumber = 0, nên văn bản đầu tiên được cấp số trong năm 2026 sẽ tự động nhận số 1 (0 + 1 = 1).
  + Sổ của năm cũ (năm 2025) sẽ được cán bộ văn thư thực hiện đóng sổ thủ công (thông qua chức năng đóng sổ, đặt is_closed = true). Khi sổ đã đóng, hệ thống chặn hoàn toàn mọi hành vi cấp số mới hoặc chỉnh sửa sổ cũ.
  + Ràng buộc năm hiệu lực: Khi cấp số, hệ thống kiểm tra chặt chẽ năm hiệu lực của văn bản phải trùng khớp với năm của sổ văn bản. Văn bản của năm 2026 không thể cấp số vào sổ của năm 2025.

- Bước 3: Điều kiện chờ cấp số sau khi lãnh đạo duyệt
  + Chỉ những văn bản đã được Lãnh đạo phê duyệt chính thức (trạng thái APPROVED) mới được phép cấp số.
  + Văn bản đang ở trạng thái soạn thảo (DRAFT), chờ duyệt hoặc bị trả về sẽ bị từ chối cấp số ngay lập tức.
  + Các thông tin của văn bản gồm loại văn bản, đơn vị ban hành và năm hiệu lực phải khớp hoàn toàn với thông tin đã đăng ký của sổ.

- Bước 4: Thao tác cấp số và cơ chế chống trùng số (Pessimistic Write Lock)
  + Cán bộ văn thư chọn sổ và thực hiện cấp số cho văn bản.
  + Để đảm bảo an toàn trong môi trường nhiều người dùng cùng thao tác đồng thời, hệ thống sử dụng khóa ghi cơ sở dữ liệu (Pessimistic Write Lock - SELECT FOR UPDATE).
  + Số thứ tự được tăng thêm 1: currentNumber = currentNumber + 1.
  + Số hiệu văn bản được tạo tự động bằng cách thay thế {NUM} trong mẫu số bằng số thứ tự mới (ví dụ: số 15 ghép vào mẫu {NUM}/QĐ-ĐHEPU thành 15/QĐ-ĐHEPU).
  + Số hiệu này được lưu vào trường official_number của văn bản.

---

## 4. Luồng Soạn Thảo, Quản Lý Phiên Bản và Trình Duyệt Văn Bản

- Bước 1: Tạo dự thảo văn bản
  + Chuyên viên nhập thông tin trích yếu, tóm tắt, nội dung, chọn loại văn bản, độ mật, độ khẩn, đơn vị ban hành, người ký dự kiến.
  + Hệ thống tự động sinh mã dự thảo (draft_code) và tạo phiên bản đầu tiên (Version 1.0).
  + Tải lên tệp đính kèm chính (DOCX hoặc PDF). Đường dẫn file được gán vào trường primary_file_path của văn bản.

- Bước 2: Quản lý đa phiên bản, So sánh và Khôi phục (Document Versioning)
  + Lưu phiên bản mới: Trong quá trình chỉnh sửa, nếu chọn lưu phiên bản mới (saveNewVersion = true), hệ thống sẽ tăng số phiên bản và lưu một bản snapshot đầy đủ (tiêu đề, trích yếu, nội dung).
  + Xem danh sách các phiên bản: Chuyên viên có thể xem lại lịch sử tất cả các phiên bản đã tạo từ trước đến nay.
  + So sánh hai phiên bản bất kỳ: Hệ thống cung cấp chức năng đối chiếu khác biệt (Version Compare) giữa 2 phiên bản (ví dụ từ bản 1 lên bản 2) để chỉ rõ những điểm thay đổi trong tiêu đề, trích yếu và nội dung.
  + Khôi phục phiên bản cũ (Rollback / Restore Version): Cho phép chuyên viên khôi phục lại dữ liệu của một phiên bản trong quá khứ và tự động tạo ra một bản snapshot mới ghi nhận thao tác khôi phục này.

- Bước 3: Xác định người duyệt phù hợp (Eligible Approvers)
  + Thay vì chỉ định cố định, hệ thống tự động lọc danh sách các cán bộ có đủ thẩm quyền phê duyệt dựa theo đơn vị ban hành của văn bản và cơ cấu tổ chức để chuyên viên lựa chọn.

- Bước 4: Gửi duyệt hoặc Thu hồi yêu cầu duyệt
  + Gửi duyệt (submit-approval): Chuyên viên chọn người duyệt và gửi đi. Hệ thống kiểm tra điều kiện hồ sơ đầy đủ (bắt buộc phải có tệp đính kèm chính hoặc nội dung tóm tắt). Văn bản chuyển sang trạng thái SUBMITTED / PENDING_REVIEW và tự động khóa quyền chỉnh sửa của chuyên viên.
  + Thu hồi yêu cầu duyệt (recall-approval): Nếu chuyên viên phát hiện sai sót sau khi gửi nhưng cán bộ duyệt chưa bắt đầu xử lý, chuyên viên được quyền chủ động thu hồi hồ sơ về lại trạng thái DRAFT để sửa đổi tiếp.

---

## 5. Luồng Thẩm Định và Ký Nháy Điện Tử

Phần ký nháy trong hệ thống hoạt động như sau:

- Bước 1: Tiếp nhận và kiểm tra
  + Cán bộ thẩm định mở xem chi tiết văn bản và đọc tệp PDF đính kèm từ đường dẫn primary_file_path.
  + Nếu hồ sơ chưa đạt: Chọn Yêu cầu chỉnh sửa (trạng thái NEEDS_REVISION) kèm nhận xét lý do để chuyển trả người soạn thảo sửa lại.

- Bước 2: Cơ chế đóng dấu ký nháy điện tử lên tệp PDF
  + Người thẩm định gửi yêu cầu ký nháy (chỉ cần nhập ghi chú/ý kiến thẩm định).
  + Hệ thống kiểm tra quyền hạn ký nháy của người dùng và kiểm tra tệp PDF nguồn có tồn tại.
  + Hệ thống lấy họ tên đầy đủ của người ký từ thông tin hồ sơ người dùng (bảng user_infos), chức danh (Cán bộ thẩm định) và thời gian thực tế ký nháy.
  + Dịch vụ xử lý PDF (PdfProcessingService dùng thư viện Apache PDFBox) tự động mở tệp PDF, tìm đến trang cuối cùng của tài liệu và vẽ trực tiếp một Dấu ký nháy điện tử vào góc dưới bên phải trang giấy (tọa độ cách mép phải 30pt, cách mép dưới 35pt).

- Bước 3: Hình thức dấu ký nháy (Có lưu chữ ký thiết kế riêng không?)
  + Trả lời về chữ ký thiết kế riêng: Trong mã nguồn hiện tại, hệ thống KHÔNG lưu trữ hay hỗ trợ tải lên hình ảnh chữ ký tay/chữ ký thiết kế riêng của từng cá nhân.
  + Thay vào đó, hệ thống sử dụng Khuôn mẫu Dấu ký nháy điện tử chuẩn hóa được lập trình vẽ tự động bằng mã lệnh đồ họa PDFBox:
    * Khung viền hình chữ nhật kích thước 190x55 pt, nét viền màu xanh dương đậm đặc trưng của trường (mã màu RGB: 16, 76, 151).
    * Nền bên trong khung được phủ màu xanh dương nhạt (mã màu RGB: 240, 245, 255).
    * Dòng 1: Tiêu đề in đậm [ DAU KY NHAY DIEN TU - EPU ].
    * Dòng 2: Họ tên cán bộ ký, tự động điền theo tài khoản đăng nhập (ví dụ: Can bo: Nguyen Van A).
    * Dòng 3: Chức vụ của cán bộ (Chuc vu: Can bo tham dinh).
    * Dòng 4: Thời gian ký nháy chính xác đến từng giây (ví dụ: Thoi gian: 23/09/2026 14:15:30).

- Bước 4: Lưu vết và chuyển trạng thái
  + Tệp PDF sau khi vẽ con dấu được lưu thành một tệp mới trong thư mục ./uploads/drafts/ với tên initial_signed_{documentId}.pdf.
  + Cột primary_file_path của văn bản được cập nhật trỏ sang tệp PDF mới này.
  + Hệ thống tính mã băm SHA-256 của tệp PDF đã ký nháy và ghi nhận vào lịch sử luồng duyệt (document_workflow_logs) tại cột signature_hash để chống chối bỏ.
  + Văn bản chuyển sang trạng thái INITIAL_SIGNED hoặc chuyển tiếp đến Lãnh đạo (LEADER_REVIEW).

---

## 6. Luồng Phê Duyệt Của Lãnh Đạo (Bút Phê Chỉ Đạo)

- Lãnh đạo xem xét nội dung và tệp PDF đã có dấu ký nháy của cán bộ thẩm định.
- Ghi ý kiến chỉ đạo (Bút phê lãnh đạo): Lãnh đạo nhập nội dung chỉ đạo điều hành (ví dụ: giao phòng ban nào chủ trì thực hiện, hạn hoàn thành khi nào). Ý kiến này được lưu vết chính thức vào dòng nhật ký luồng duyệt.
- Lựa chọn hành động:
  + Phê duyệt (trạng thái APPROVED): Văn bản hoàn thành phê duyệt, chuyển sang hàng đợi của Cán bộ văn thư để cấp số và phát hành.
  + Yêu cầu chỉnh sửa (trạng thái NEEDS_REVISION): Chuyển trả lại cho chuyên viên kèm theo ý kiến chỉ đạo để sửa lại.
  + Từ chối phê duyệt (trạng thái REJECTED): Chấm dứt xử lý văn bản.

---

## 7. Luồng Phát Hành và Phân Phối Văn Bản

- Bước 1: Cán bộ văn thư phát hành
  + Cán bộ văn thư tiếp nhận văn bản đã duyệt và đã cấp số.
  + Thiết lập ngày ban hành chính thức (issue_date) và ngày có hiệu lực (effective_date).
  + Chọn phạm vi phát hành: phát hành toàn đơn vị/toàn trường (ALL) hoặc phát hành theo danh sách đơn vị/phòng ban nhận cụ thể (ORG).
  + Ký phát hành văn bản, chuyển trạng thái sang PUBLISHED.

- Bước 2: Bảo mật chống sửa đổi (DevSecOps)
  + Tệp PDF ban hành chính thức được lưu đường dẫn vào official_file_path.
  + Hệ thống tự động tính mã băm SHA-256 trên toàn bộ nội dung tệp PDF chính thức và lưu vào trường official_hash của văn bản.
  + Ngoài ra, hệ thống hỗ trợ tích hợp chữ ký số PKI thông qua chứng thư số X.509 lưu trong kho khóa epu-pki.p12 để đóng dấu số chứng nhận cho văn bản.

- Bước 3: Phân phối và ghi nhận nhật ký
  + Hệ thống tạo các bản ghi phân phối (DocumentDistribution) cho các đơn vị nhận.
  + Các đơn vị nhận được thông báo, có thể tra cứu, đọc văn bản và tải tệp PDF chính thức.
  + Khi người nhận xem hoặc tải tệp, hệ thống cập nhật trạng thái đã đọc (is_read = true) và thời gian đọc (read_at).

---

## 8. Luồng Tra Cứu, Tải Tệp và Kiểm Tra Tính Toàn Vẹn Của Văn Bản

- Tra cứu văn bản đã phát hành theo đơn vị nhận:
  + Người dùng tra cứu danh sách văn bản ban hành thông qua API /documents/published.
  + Hệ thống tự động lọc theo đơn vị của người dùng (orgId): người dùng chỉ xem được các văn bản phát hành cho toàn trường hoặc phát hành trực tiếp cho đơn vị của mình.

- Xem chi tiết và Tải bản PDF chính thức:
  + Người dùng có thể xem trích yếu đầy đủ qua /documents/{id}/details.
  + Tải trực tiếp bản PDF chính thức đã đóng dấu chữ ký qua /documents/{id}/download.

- Kiểm tra tính toàn vẹn của văn bản (Verify Integrity):
  + Người dùng hoặc cán bộ kiểm tra gọi API /documents/{id}/verify-integrity.
  + Hệ thống đọc tệp PDF hiện tại trên ổ đĩa, tính toán lại mã băm SHA-256 thực tế và đối chiếu với mã băm official_hash đã lưu lúc phát hành.
  + Nếu mã băm khớp 100%: Hệ thống trả về kết quả hợp lệ (valid = true), chứng minh tệp tin nguyên bản, chưa từng bị thay đổi.
  + Nếu có bất kỳ sự can thiệp, chỉnh sửa dù chỉ 1 ký tự: Mã băm không khớp, hệ thống trả về valid = false để cảnh báo tài liệu đã bị sửa đổi trái phép.

---

## 9. Luồng Giám Sát Tiến Trình (Timeline) và Cảnh Báo Tồn Đọng Theo Hạn Xử Lý (SLA)

- Xem Timeline luồng điều hành:
  + Người dùng có thể xem toàn bộ hành trình của văn bản qua API /documents/{id}/timeline hoặc /documents/{id}/workflow-logs.
  + Timeline ghi lại chi tiết từng mốc: Người thực hiện, chức vụ, hành động (tạo nháp, gửi duyệt, ký nháy, phê duyệt), thời gian chính xác và nội dung bút phê chỉ đạo của Lãnh đạo.

- Quản lý văn bản tồn đọng theo thời hạn xử lý (SLA):
  + Cán bộ quản lý hoặc lãnh đạo tra cứu danh sách văn bản tồn đọng qua API /documents/pending-review.
  + Hệ thống tự động tính toán số ngày mà văn bản đã nằm chờ xử lý ở trạng thái hiện tại.
  + Giúp lãnh đạo và văn thư phát hiện các văn bản bị trễ hạn, tồn đọng quá lâu để đôn đốc cán bộ xử lý kịp thời.

---

## 10. Tóm Tắt Vòng Đời Trạng Thái Văn Bản

- DRAFT (Bản nháp): Chuyên viên đang soạn thảo, tệp đính kèm được lưu tạm trong uploads/drafts/, được phép sửa đổi thoải mái.
- SUBMITTED / PENDING_REVIEW (Chờ thẩm định): Đã gửi đi, hồ sơ bị khóa chỉnh sửa, chờ cán bộ thẩm định xem xét.
- NEEDS_REVISION (Yêu cầu chỉnh sửa): Hồ sơ bị trả về kèm nhận xét, chuyên viên mở lại quyền cập nhật để sửa đổi.
- INITIAL_SIGNED (Đã ký nháy): Cán bộ thẩm định đã kiểm tra và đóng Dấu ký nháy điện tử chuẩn hóa vào trang cuối của tệp PDF.
- APPROVED (Đã phê duyệt): Lãnh đạo đã duyệt và ghi bút phê chỉ đạo, văn bản chuyển sang văn thư chờ cấp số.
- PUBLISHED (Đã ban hành chính thức): Văn thư đã cấp số từ sổ văn bản, đóng dấu băm SHA-256 chống chỉnh sửa và phân phối đến các đơn vị nhận.
- REJECTED (Bị từ chối): Lãnh đạo không đồng ý, văn bản dừng quy trình xử lý.

---

## 11. Luồng Xác Thực và Quản Lý Phiên (Authentication & JWT Dual-Token)

Hệ thống eoffice-be đã triển khai hoàn chỉnh luồng xác thực bảo mật chuẩn RFC 6749 với cơ chế 2 token (Dual-Token):

- Cơ chế Đăng ký và Đăng nhập (Register & Login):
  + Người dùng đăng nhập qua API /auth/login bằng username và password.
  + Mật khẩu được băm bảo mật bằng thuật toán BCrypt.
  + Bảo vệ chống dò quét (Brute-force): Hệ thống tự động đếm số lần đăng nhập sai. Nếu sai quá 5 lần liên tiếp, tài khoản tự động bị khóa (trạng thái ACCOUNT_LOCKED).
  + Rate Limiting: Giới hạn tần suất gọi API đăng nhập theo địa chỉ IP để chống tấn công từ chối dịch vụ.

- Cơ chế 2 Token (Access Token & Refresh Token):
  + Access Token (ngắn hạn): Trả về trực tiếp trong response body JSON. Dùng để gửi kèm trong header Authorization: Bearer {token} của mỗi request.
  + Refresh Token (dài hạn): Được gắn tự động vào cookie trình duyệt với cờ HttpOnly, Path=/auth, SameSite=Strict. JavaScript ở Frontend không thể đọc được cookie này, giúp chống triệt để tấn công XSS.

- Cơ chế Xoay Vòng Token (Refresh Token Rotation) và Phát Hiện Token Bị Lộ:
  + Khi Access Token hết hạn, Frontend gọi API /auth/refresh.
  + Hệ thống kiểm tra mã định danh JTI của Refresh Token trong bảng refresh_tokens.
  + Nếu hợp lệ: Cấp một cặp Access Token và Refresh Token mới, đồng thời đánh dấu Refresh Token cũ đã bị thu hồi (is_revoked = true).
  + Phát hiện tấn công đánh cắp token (Reuse Detection): Nếu một Refresh Token đã bị thu hồi mà lại xuất hiện gọi đến hệ thống, hệ thống xác định kẻ gian đã đánh cắp token và lập tức hủy bỏ toàn bộ mọi phiên đăng nhập của người dùng đó.

- Luồng Đăng xuất (Logout):
  + Khi người dùng đăng xuất qua /auth/logout, Refresh Token hiện tại được đánh dấu thu hồi vĩnh viễn và cookie bị xóa bỏ khỏi trình duyệt.

- Luồng Quên Mật Khẩu (Forgot / Reset Password):
  + Người dùng gửi yêu cầu qua /auth/forgot-password.
  + Hệ thống sinh token đặt lại mật khẩu ngẫu nhiên dùng 1 lần (có thời hạn ngắn) và gửi email hướng dẫn. Response luôn trả về thành công để tránh kẻ xấu dò xem email có tồn tại trong hệ thống hay không.
  + Người dùng dùng token này để nhập mật khẩu mới qua /auth/reset-password.

---

## 12. Luồng Phân Quyền Ma Trận 3 Lớp (RBAC & Cây Tổ Chức)

Hệ thống eoffice-be thiết kế mô hình phân quyền ma trận 3 lớp (RBAC Matrix) linh hoạt, không phụ thuộc cứng vào token tĩnh:

- Nguyên lý Phân giải quyền động (Runtime Permission Resolution):
  + JWT Token chỉ lưu Role hệ thống cơ bản, KHÔNG nhúng cứng danh sách quyền chi tiết.
  + Khi người dùng gọi một chức năng, lớp RbacPermissionEvaluator sẽ truy vấn động từ cơ sở dữ liệu để kiểm tra quyền hạn thực tế ngay tại thời điểm đó. Nhờ vậy, khi một cán bộ được bổ nhiệm, miễn nhiệm hoặc điều chuyển đơn vị, quyền hạn thay đổi tức thì mà không cần cán bộ phải đăng xuất hay cấp lại token.

- Lớp 1: Vai trò toàn cục (Global Role):
  + SYSTEM_ADMIN: Quản trị viên tối cao, tự động vượt qua mọi bước kiểm tra quyền ở tất cả các phòng ban trong toàn trường.
  + ADMIN: Quản trị viên hệ thống, quản lý cơ cấu tổ chức, người dùng và sổ văn bản.
  + USER: Cán bộ/giảng viên bình thường, quyền hạn xử lý công việc cụ thể sẽ phụ thuộc vào vị trí được bổ nhiệm tại đơn vị.

- Lớp 2: Quyền theo Vị trí và Cây đơn vị (Position Scope & Org Hierarchy):
  + Một người dùng có thể kiêm nhiệm nhiều chức vụ tại nhiều đơn vị khác nhau (ví dụ: vừa là Trưởng khoa CNTT, vừa là Thành viên Hội đồng trường) thông qua bảng position_assignments.
  + Phạm vi quyền hạn gắn liền với đơn vị: Trưởng khoa CNTT chỉ có quyền duyệt hồ sơ nội bộ Khoa CNTT, không có quyền can thiệp vào hồ sơ của Khoa khác.
  + Cơ chế Kế thừa quyền theo cây tổ chức (Closure Table): Nhờ bảng org_node_closures, nếu vị trí được cấu hình inherit_to_children = true, cán bộ ở đơn vị cấp trên (ví dụ: Trưởng khoa) sẽ tự động sở hữu quyền quản lý ở tất cả các đơn vị trực thuộc cấp dưới (Bộ môn Phần mềm, Bộ môn Mạng,...).

- Lớp 3: Cơ chế Ủy quyền tạm thời (Delegation):
  + Trong bảng position_degs, một cán bộ (ví dụ: Trưởng phòng đi công tác) có thể ủy quyền một phần hoặc toàn bộ quyền hạn xử lý văn bản cho cấp phó trong một khoảng thời gian xác định (từ start_date đến end_date).
  + Khi kiểm tra quyền, hệ thống kiểm tra nếu có bản ghi ủy quyền còn hiệu lực thì người được ủy quyền sẽ được phép thao tác thay.

---

## 13. Luồng Quản Lý Cơ Cấu Cây Tổ Chức (OrgNode & Closure Table)

Được quản lý thông qua OrgNodeController:

- Cấu trúc cây đơn vị phòng ban:
  + Quản lý các nút đơn vị (Trường -> Khoa / Phòng ban -> Bộ môn / Tổ chức năng).
  + Mỗi đơn vị có mã định danh, tên đơn vị, cấp độ (level) và đơn vị cha (parent_id).

- Giải pháp bảng đường đi đa cấp (Closure Table - org_node_closures):
  + Thay vì truy vấn đệ quy chậm chạp, hệ thống sử dụng bảng Closure Table lưu trữ tất cả các cặp quan hệ tổ tiên - hậu duệ (ancestor - descendant) cùng độ sâu (depth).
  + Giúp truy vấn toàn bộ cây đơn vị con của một phòng ban/khoa chỉ với một câu lệnh SQL duy nhất và hỗ trợ kế thừa quyền lực phân cấp tức thì.

---

## 14. Luồng Quản Lý Danh Mục Dùng Chung (Dictionary Items)

Được quản lý thông qua DictionaryItemController:

- Quản lý danh mục các mã nghiệp vụ chuẩn hóa:
  + Loại văn bản (DOCUMENT_TYPE): Quyết định (QĐ), Công văn (CV), Kế hoạch (KH), Thông báo (TB), Tờ trình (TT), Biên bản (BB)...
  + Độ mật văn bản (DOCUMENT_SECURITY_LEVEL): Thường (NORMAL), Mật (CONFIDENTIAL), Tối mật (SECRET), Tuyệt mật (TOP_SECRET).
  + Độ khẩn văn bản (DOCUMENT_URGENCY_LEVEL): Thường (NORMAL), Khẩn (URGENT), Thượng khẩn (VERY_URGENT), Hỏa tốc (FLASH).
- Cung cấp dữ liệu danh mục để người dùng lựa chọn khi tạo văn bản hoặc tạo sổ văn bản.

---

## 15. Luồng Quản Trị Người Dùng và Hồ Sơ (Admin User Management)

Được quản lý thông qua AdminUserController:

- Quản lý tài khoản đăng nhập (user_logins):
  + Quản trị viên có thể tạo mới, cập nhật vai trò (Role), kích hoạt hoặc vô hiệu hóa tài khoản.
  + Khóa / Mở khóa tài khoản: Khi người dùng bị khóa do nhập sai mật khẩu 5 lần, quản trị viên có thể mở khóa tài khoản thủ công.
  + Đặt lại mật khẩu cho cán bộ.

- Hồ sơ thông tin cá nhân (user_infos):
  + Quản lý thông tin chi tiết: Họ tên, ngày sinh, số căn cước, học hàm, học vị, chức danh, ngày vào trường, email liên hệ, số điện thoại.
  + Thông tin này được liên kết trực tiếp với tài khoản và được trích xuất tự động khi cán bộ thực hiện ký nháy hoặc duyệt văn bản.

---

## 16. Luồng Kiểm Toán và Nhật Ký Biến Động (Audit & Change Tracking)

Hệ thống đã triển khai đầy đủ kiến trúc kiểm toán 3 tầng độc lập, phục vụ truy vết mọi hành vi và sự thay đổi dữ liệu:

- Tầng 1: Nhật ký truy cập API người dùng (@Auditable -> user_audit_logs):
  + Gắn annotation @Auditable lên Controller.
  + Cơ chế AOP tự động bắt chặn mọi request gọi vào API và gửi sự kiện xử lý bất đồng bộ (Async Event) qua AuditLogAsyncService. Việc ghi nhật ký chạy ở luồng nền (background thread), hoàn toàn không làm chậm tốc độ phản hồi của API chính.
  + Thông tin ghi lại gồm: Tên tài khoản thực hiện, Địa chỉ IP thực tế (hỗ trợ qua Reverse Proxy/X-Forwarded-For), User-Agent thiết bị/trình duyệt, Đường dẫn URI, Thời gian thực thi API (mili-giây), Tham số và Body gửi lên dạng JSON, Trạng thái thành công hay thất bại.
  + Quản trị viên tra cứu nhật ký truy cập qua UserAuditLogController.

- Tầng 2: Nhật ký lịch sử biến động dữ liệu (@TrackChange -> change_history_logs):
  + Gắn annotation @TrackChange lên các hàm nghiệp vụ Service khi thực hiện cập nhật dữ liệu quan trọng.
  + Hệ thống chụp lại trạng thái trước khi thay đổi (Before Snapshot) và sau khi thay đổi (After Snapshot).
  + Tự động so sánh (JSON Diff) và chỉ ra chính xác các trường dữ liệu nào bị thay đổi, giá trị cũ là gì, giá trị mới là gì.
  + Toàn bộ thông tin được lưu trữ vào trường JSONB của bảng change_history_logs trong PostgreSQL, giúp quản trị viên có thể đối soát và khôi phục dữ liệu khi cần.
  + Quản trị viên tra cứu lịch sử thay đổi qua ChangeHistoryLogController.

- Tầng 3: Tự động ghi vết JPA Audit (BaseEntity):
  + Tất cả các bảng thực thể (Document, DocumentBook, UserInfo,...) đều kế thừa từ BaseEntity.
  + Hệ thống tự động điền và cập nhật 4 trường thông tin cơ bản trên từng dòng dữ liệu: Người tạo (created_by), Thời gian tạo (created_date), Người sửa đổi cuối cùng (last_modified_by), Thời gian sửa đổi cuối cùng (last_modified_date).

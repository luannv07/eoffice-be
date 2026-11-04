# 📘 BÁO CÁO CHUYÊN ĐỀ TỐT NGHIỆP / ĐỒ ÁN KỸ THUẬT

---

> **TÊN ĐỀ TÀI:**  
> **"Nghiên cứu quy trình DevSecOps và các kỹ thuật lập trình an toàn ứng dụng trong phát triển thử nghiệm hệ thống thông tin hỗ trợ điều hành các hoạt động của một đơn vị trên văn bản điện tử"**  
> *(Hệ thống Văn phòng điện tử e-Office - Trường Đại học Điện Lực)*

---

## 📑 CẤU TRÚC TỔNG THỂ CUỐN BÁO CÁO CHUYÊN ĐỀ SƠ BỘ (2 CHƯƠNG)

Cuốn báo cáo chuyên đề sơ bộ được thiết kế gồm 02 chương trọng tâm, đóng vai trò bản lề định hình toàn bộ cơ sở khoa học, phương pháp luận kỹ thuật và kiến trúc giải pháp cho hệ thống:

* **CHƯƠNG 1: KHẢO SÁT HIỆN TRẠNG VÀ XÁC ĐỊNH MỤC TIÊU ĐỀ TÀI** *(Nội dung chi tiết trong tài liệu này)*
  * Đặt vấn đề, phân tích tính cấp thiết của chuyển đổi số văn bản hành chính gắn liền với an toàn thông tin.
  * Khảo sát hiện trạng nghiệp vụ quản lý văn bản, điều hành tác nghiệp tại các đơn vị giáo dục đại học (điển hình tại Trường Đại học Điện Lực - EPU).
  * Khảo sát các giải pháp công nghệ hiện hành, phân tích khoảng trống kỹ thuật (Gap Analysis).
  * Khảo sát quy trình DevSecOps ("Shift-Left") và các tiêu chuẩn lập trình an toàn (Secure Coding).
  * Xác định đối tượng, phạm vi nghiên cứu, mục tiêu tổng quát và các mục tiêu cụ thể.
  * Phương pháp luận nghiên cứu và kế hoạch triển khai đề tài.

* **CHƯƠNG 2: TỔNG QUAN CƠ SỞ LÝ THUYẾT VÀ THIẾT KẾ KIẾN TRÚC HỆ THỐNG AN TOÀN** *(Đề cương tóm tắt dự kiến)*
  * Cơ sở lý thuyết về an ninh ứng dụng: Mô hình đe dọa (Threat Modeling - STRIDE), tiêu chuẩn OWASP Top 10 Web Application Security Risks, chuẩn phân quyền RBAC/ABAC.
  * Cơ sở lý thuyết mật mã và chữ ký số: Mật mã bất đối xứng RSA, hạ tầng khóa công khai PKI X.509, chuẩn chữ ký số văn bản PDF PAdES (PDF Advanced Electronic Signatures) và niêm phong tài liệu DocMDP.
  * Khung quy trình DevSecOps: Thiết kế đường ống tự động hóa (CI/CD Pipeline) tích hợp SAST (SonarQube), SCA (OWASP Dependency-Check), DAST (OWASP ZAP) và Secret Scanning.
  * Phân tích và thiết kế kiến trúc hệ thống: Mô hình phân tầng nghiệp vụ (Layered Architecture), biểu đồ luồng dữ liệu (DFD), ma trận Use Case cốt lõi và thiết kế cơ sở dữ liệu (ERD) xử lý phân cấp cây tổ chức động (Closure Table).

---

# 📖 CHƯƠNG 1: KHẢO SÁT HIỆN TRẠNG VÀ XÁC ĐỊNH MỤC TIÊU ĐỀ TÀI

---

## 1.1. ĐẶT VẤN ĐỀ VÀ TÍNH CẤP THIẾT CỦA ĐỀ TÀI

### 1.1.1. Bối cảnh chuyển đổi số quốc gia trong công tác quản lý văn bản điện tử
Trong bối cảnh cuộc Cách mạng Công nghiệp lần thứ tư (Industry 4.0), việc ứng dụng công nghệ thông tin vào công tác quản trị, điều hành hành chính tại các cơ quan Nhà nước, tổ chức sự nghiệp công lập và các cơ sở giáo dục đại học là xu thế tất yếu. Đảng và Nhà nước Việt Nam đã ban hành nhiều chủ trương, hành lang pháp lý thúc đẩy quá trình này:
- **Chương trình Chuyển đổi số quốc gia đến năm 2025, định hướng đến năm 2030** (Quyết định số 749/QĐ-TTg ngày 03/06/2020 của Thủ tướng Chính phủ) đặt mục tiêu phát triển Chính phủ số, kinh tế số và xã hội số, trong đó 100% hồ sơ công việc tại cấp bộ, cấp tỉnh được xử lý trên môi trường mạng.
- **Nghị định số 30/2020/NĐ-CP ngày 05/03/2020 của Chính phủ về công tác văn thư**: Đã chính thức luật hóa giá trị pháp lý của văn bản điện tử và quy định chặt chẽ về thể thức, kỹ thuật trình bày, quy trình soạn thảo, thẩm định, ký nháy, ký số người có thẩm quyền, cấp số và phát hành văn bản điện tử.
- **Luật Giao dịch điện tử số 20/2023/QH15** (có hiệu lực từ ngày 01/07/2024): Mở rộng phạm vi và công nhận giá trị pháp lý bình đẳng của thông điệp dữ liệu, chữ ký điện tử an toàn và chứng thư chữ ký điện tử so với chữ ký tay và con dấu truyền thống.

Tại các cơ sở giáo dục đại học đa ngành, đa lĩnh vực có quy mô lớn như **Trường Đại học Điện Lực (EPU)**, khối lượng văn bản hành chính (Quyết định, Thông báo, Kế hoạch, Tờ trình, Công văn, Hướng dẫn, Biên bản...) phát sinh hàng ngày là rất lớn. Việc chuyển đổi từ phương thức quản lý giấy tờ truyền thống sang mô hình Văn phòng điện tử (e-Office) là điều kiện tiên quyết nhằm tối ưu hóa chi phí vận hành, rút ngắn thời gian xử lý thủ tục hành chính, tăng cường tính minh bạch và nâng cao năng lực chỉ đạo, điều hành của Ban Giám hiệu.

### 1.1.2. Những bất cập và thách thức trong quản trị văn bản truyền thống
Thực tế khảo sát tại các đơn vị hành chính sự nghiệp và trường đại học cho thấy mô hình xử lý văn bản truyền thống (dựa trên văn bản giấy hoặc xử lý phân tán qua email, ứng dụng nhắn tin) đang bộc lộ nhiều điểm nghẽn nghiêm trọng:
1. **Lãng phí thời gian và nguồn lực vật lý**: Quy trình trình ký thủ công đòi hỏi chuyên viên phải in ấn bản thảo, trình qua nhiều cấp lãnh đạo (Trưởng bộ môn $\rightarrow$ Trưởng phòng/Ban $\rightarrow$ Ban Giám hiệu) để xin chữ ký nháy và chữ ký chính thức. Việc lưu trữ hồ sơ giấy tiêu tốn diện tích kho lưu trữ, chi phí văn phòng phẩm và nhân lực quản thư.
2. **Đứt gãy luồng thông tin và khó truy vết tiến trình**: Khi một hồ sơ bị chậm trễ hoặc từ chối, việc tìm kiếm hồ sơ đang nằm ở bàn của ai, lý do trả về là gì thường rất khó khăn. Lãnh đạo không nắm được bức tranh tổng thể về tiến độ xử lý công việc của từng phòng ban.
3. **Nguy cơ sai lệch, trùng lặp và thất thoát số hiệu văn bản**: Việc ghi chép sổ văn bản bằng sổ tay giấy hoặc bảng tính Excel phân tán dễ dẫn đến tình trạng cấp trùng số, cấp sót số, hoặc sửa lùi ngày ban hành trái quy định.
4. **Thiếu liên kết trong chuỗi văn bản điều hành**: Một quyết định ban hành thường kéo theo các kế hoạch thực hiện, báo cáo kết quả hoặc tờ trình điều chỉnh. Trên văn bản giấy, việc tra cứu ngược chuỗi văn bản cha - con (Document Lineage) đòi hỏi lục tìm hồ sơ thủ công mất nhiều thời giờ.

### 1.1.3. Thách thức về an ninh, an toàn thông tin và sự thiếu vắng của DevSecOps
Song song với bài toán nghiệp vụ, một thách thức cấp bách hơn thuộc về khía cạnh **An toàn thông tin (Application Security)**:
- **Tình trạng "phát triển trước - bảo mật sau"**: Trong mô hình truyền thống (Waterfall hoặc Agile thông thường), khâu an toàn thông tin chỉ được xem xét ở giai đoạn cuối cùng (sau khi phần mềm đã hoàn thiện và chuẩn bị triển khai). Lúc này, việc phát hiện các lỗ hổng kiến trúc, lỗi logic cấp quyền hoặc lỗ hổng thư viện bên thứ ba (Third-party dependencies) sẽ dẫn đến chi phí khắc phục vô cùng tốn kém, kéo dài thời gian phát hành hoặc buộc phải chấp nhận vận hành hệ thống với nhiều rủi ro tiềm ẩn.
- **Nguy cơ tấn công ứng dụng Web**: Các hệ thống văn phòng điện tử lưu trữ nhiều tài liệu nội bộ, thông tin nhân sự và kế hoạch chiến lược. Nếu thiếu kỹ thuật lập trình an toàn (Secure Coding), hệ thống rất dễ trở thành mục tiêu của các cuộc tấn công phổ biến theo phân loại của OWASP Top 10:
  - *Broken Access Control (IDOR, Privilege Escalation)*: Người dùng có thể đoán URL để tải trộm tệp dự thảo hoặc can thiệp duyệt văn bản ngoài thẩm quyền đơn vị của mình.
  - *Injection & Path Traversal*: Kẻ tấn công lợi dụng việc tải tệp đính kèm để ghi đè tệp hệ thống hoặc chèn mã độc.
  - *Race Condition*: Nhiều cán bộ văn thư đồng thời cấp số dẫn đến hiện tượng xung đột dữ liệu, trùng số văn bản.
- **Nguy cơ làm giả, chỉnh sửa văn bản sau khi ký**: Nhiều phần mềm quản lý nội bộ chỉ lưu trữ "ảnh chụp chữ ký" (chữ ký dạng hình ảnh PNG chèn vào tệp tài liệu) hoặc chỉ ghi nhận một cờ trạng thái `is_signed = true` trong cơ sở dữ liệu. Về mặt kỹ thuật, tệp PDF này hoàn toàn có thể bị chỉnh sửa nội dung bằng các công cụ chỉnh sửa tệp thông thường mà người xem không thể phát hiện, làm mất hoàn toàn tính pháp lý và tính chống chối bỏ (Non-repudiation).

Từ những lý do trên, việc **"Nghiên cứu quy trình DevSecOps và các kỹ thuật lập trình an toàn ứng dụng trong phát triển thử nghiệm hệ thống thông tin hỗ trợ điều hành các hoạt động của một đơn vị trên văn bản điện tử"** mang tính cấp thiết cả về mặt khoa học công nghệ lẫn thực tiễn quản lý.

---

## 1.2. KHẢO SÁT HIỆN TRẠNG VÀ PHÂN TÍCH YÊU CẦU NGHIỆP VỤ ĐIỀU HÀNH TÁC NGHIỆP

### 1.2.1. Khảo sát mô hình cơ cấu tổ chức và phân quyền quản lý
Khảo sát thực tế cơ cấu tổ chức của một cơ sở giáo dục đại học như Trường Đại học Điện Lực (EPU) cho thấy:
- **Cơ cấu tổ chức dạng cây đa tầng, phân cấp sâu**:
  - Cấp cao nhất: Hội đồng Trường, Ban Giám hiệu (Hiệu trưởng, các Phó Hiệu trưởng).
  - Cấp trung gian: Các Phòng chức năng (Phòng Đào tạo, Phòng Tổ chức Cán bộ, Phòng Kế hoạch Tài chính, Phòng Khoa học Công nghệ...), các Khoa đào tạo (Khoa Công nghệ Thông tin, Khoa Điện lực, Khoa Kinh tế & Quản lý...), các Viện nghiên cứu, Trung tâm trực thuộc.
  - Cấp cơ sở: Các Bộ môn, Tổ công tác trực thuộc Khoa/Phòng.
- **Đặc thù luân chuyển và điều chỉnh tổ chức**:
  - Mô hình tổ chức không tĩnh mà có thể biến động theo thời gian: sát nhập phòng ban, đổi tên khoa, tách bộ môn (Re-parenting). Khi cây tổ chức thay đổi vị trí của một nút, hệ thống phải đảm bảo các quan hệ quản lý cấp trên - cấp dưới tự động cập nhật mà không làm đứt gãy luồng văn bản đang xử lý.
- **Ma trận phân quyền phức hợp**:
  - Phân quyền theo vai trò toàn cục (Global Role): `SYSTEM_ADMIN`, `LEADER`, `REVIEWER`, `SPECIALIST`, `CLERK`.
  - Phân quyền theo phạm vi vị trí chức vụ (Position Scope): Quyền hạn của Trưởng khoa chỉ có hiệu lực với các văn bản nội bộ thuộc khoa mình quản lý; Trưởng phòng chức năng có thẩm quyền ký nháy các văn bản chuyên môn thuộc lĩnh vực phụ trách.
  - Cơ chế ủy quyền (Delegation): Khi Lãnh đạo đi công tác, thẩm quyền phê duyệt văn bản có thể được ủy quyền tạm thời cho cấp phó trong một khoảng thời gian nhất định và với một phạm vi loại văn bản cụ thể.

### 1.2.2. Khảo sát chu trình vòng đời văn bản hành chính điện tử
Dựa trên quy chuẩn của Nghị định 30/2020/NĐ-CP và khảo sát quy trình tác nghiệp nội bộ, vòng đời của một văn bản đi/chỉ đạo điều hành được chuẩn hóa thành 5 giai đoạn:

```
[1. Dự thảo] ──► [2. Thẩm định / Ký nháy] ──► [3. Phê duyệt Lãnh đạo] ──► [4. Cấp số & Ban hành] ──► [5. Lưu trữ / Theo dõi]
     │                       │                           │
     ▼                       ▼                           ▼
(Chuyên viên)         (Trưởng phòng/Khoa)           (Hiệu trưởng)            (Cán bộ Văn thư)             (Toàn trường)
- Soạn thảo nội dung  - Đọc, soát xét           - Đọc nội dung              - Kiểm tra sổ văn bản        - Tra cứu theo quyền
- Tải file đính kèm   - Yêu cầu sửa (trả về)    - Ghi ý kiến bút phê        - Cấp số tự động (Pessimistic- Tải bản PDF chính thức
- Quản lý phiên bản   - Từ chối văn bản         - Trả về / Từ chối          - Ký số phát hành (X.509)   - Xem Document Lineage
- Chọn luồng duyệt    - Ký nháy số (Digital     - Phê duyệt chủ trương      - Niêm phong DocMDP
                        Stamp + Hash SHA-256)                               - Phân phối đến đơn vị nhận
```

1. **Giai đoạn 1: Soạn thảo và quản lý dự thảo (Drafting & Versioning)**:
   - Cán bộ chuyên viên khởi tạo dự thảo, nhập trích yếu, tóm tắt, lựa chọn loại văn bản (Quyết định, Thông báo, Kế hoạch...), mức độ khẩn/mật, và tải lên tệp tài liệu chính (DOCX/PDF) cùng các tệp phụ lục.
   - Hỗ trợ quản lý đa phiên bản (Document Versioning): Khi chuyên viên cập nhật nội dung, hệ thống lưu lại các bản snapshot (Version 1.0, 1.1, 2.0...), cho phép xem lại lịch sử thay đổi, so sánh sự khác biệt giữa hai phiên bản bất kỳ và khôi phục (rollback) về phiên bản trước đó nếu cần.
2. **Giai đoạn 2: Thẩm định và ký nháy điện tử (Reviewing & Electronic Initialing)**:
   - Hồ sơ được gửi tới cán bộ thẩm định (Trưởng phòng/Trưởng đơn vị liên quan).
   - Người thẩm định có thể yêu cầu chỉnh sửa (`NEEDS_REVISION`), từ chối (`REJECTED`) hoặc đồng thuận và thực hiện **ký nháy điện tử (Digital Initialing Stamp)**.
   - Khi ký nháy, hệ thống phải nhúng dấu ký nháy (họ tên, chức vụ, thời điểm, ảnh chữ ký mẫu) vào trang tài liệu PDF và tính toán mã băm SHA-256 để lưu vết, đảm bảo người soạn thảo không thể âm thầm thay đổi nội dung sau khi đã được thẩm định.
3. **Giai đoạn 3: Phê duyệt của Lãnh đạo đơn vị (Executive Approval)**:
   - Văn bản sau khi có đủ chữ ký nháy sẽ được chuyển tiếp tới Lãnh đạo (Hiệu trưởng / Phó Hiệu trưởng).
   - Lãnh đạo ghi nhận **bút phê điều hành (Executive Endorsement)** gồm các ý kiến chỉ đạo, thời hạn thực hiện, giao đơn vị chủ trì, và phê duyệt (`APPROVED`) thông qua nội dung văn bản.
4. **Giai đoạn 4: Cấp số và phát hành có chữ ký số (Registration & Digital Signature Issuance)**:
   - Văn bản đã duyệt được chuyển tới Bộ phận Văn thư.
   - **Quản lý Sổ văn bản 2 cấp**: Hệ thống quản lý Sổ văn bản Cấp 1 (Cấp Trường/Đơn vị ban hành như Quyết định của Hiệu trưởng) và Sổ văn bản Cấp 2 (Cấp Phòng/Ban/Khoa ban hành văn bản nội bộ). Mỗi sổ văn bản gắn liền với một loại văn bản và chu kỳ năm hành chính.
   - **Cấp số tự động**: Hệ thống cấp số theo định dạng `{NUM}/MÃ_LOẠI-MÃ_ĐƠN_VỊ` (ví dụ: `125/QĐ-ĐHEPU`). Quá trình cấp số phải có cơ chế khóa chống trùng số tuyệt đối khi có nhiều giao dịch phát hành đồng thời. Số thứ tự tự động reset về 0 vào ngày 01/01 hàng năm khi tạo sổ mới.
   - **Ký số phát hành và Niêm phong tài liệu (Digital Signing & DocMDP)**: Lãnh đạo hoặc Người có thẩm quyền ký số bằng chứng thư số PKI X.509 chuẩn PAdES. Sau khi ký, tài liệu được áp dụng cơ chế khóa niêm phong DocMDP ($P=1$), ngăn chặn mọi hành vi thay đổi cấu trúc và nội dung tài liệu.
5. **Giai đoạn 5: Lưu trữ, Phân phối, Tra cứu và Kiểm định (Distribution & Verification)**:
   - Văn bản phát hành được tự động phân phối tới các đơn vị nhận trên cây tổ chức.
   - Hỗ trợ xem chuỗi văn bản liên kết (Document Lineage) giúp theo dõi một Quyết định gốc sinh ra những Kế hoạch và Báo cáo nào.
   - Cung cấp cổng kiểm tra tính toàn vẹn và xác minh chữ ký số độc lập cho người dùng bên trong và bên ngoài đơn vị.

---

## 1.3. KHẢO SÁT CÁC GIẢI PHÁP HIỆN HÀNH VÀ PHÂN TÍCH KHOẢNG TRỐNG KỸ THUẬT (GAP ANALYSIS)

### 1.3.1. Đánh giá các giải pháp phần mềm trên thị trường
Hiện nay, trên thị trường Việt Nam có một số hệ thống quản lý văn bản và điều hành tác nghiệp được triển khai:

| Tiêu chí so sánh | Phần mềm thương mại đóng gói (VNPT iOffice, Viettel vOffice) | Các hệ thống mã nguồn mở tự xây dựng thông thường | Hệ thống đề tài hướng tới (`eoffice-be`) |
| :--- | :--- | :--- | :--- |
| **Mục đích & Đối tượng** | Dành cho khối cơ quan Nhà nước quy mô lớn | Ứng dụng nội bộ nhỏ, doanh nghiệp tư nhân | Tối ưu hóa đặc thù quản trị trường đại học đa cấp (Khoa/Phòng/Viện) |
| **Chi phí bản quyền & Triển khai** | Rất cao, chi phí bảo trì và cấp chứng thư hàng năm lớn | Thấp, mã nguồn mở | Thấp, mã nguồn mở, độc lập và dễ dàng đóng gói Docker |
| **Khả năng tùy biến luồng** | Phức tạp, phụ thuộc hoàn toàn vào nhà cung cấp | Hạn chế, cấu hình luồng cứng (Hardcoded) | Linh hoạt, hỗ trợ tái cơ cấu cây tổ chức động (Closure Table) |
| **Xử lý Sổ văn bản** | Sổ tập trung 1 cấp theo mẫu chuẩn bộ ngành | Đa phần là bảng tính số đơn giản, dễ trùng số | **Quản lý Sổ văn bản 2 cấp** (Trường & Phòng/Khoa), khóa ghi chống xung đột |
| **Chữ ký số & Tính toàn vẹn** | Phụ thuộc USB Token của nhà mạng, khó tự động hóa API | Thường chỉ lưu ảnh chữ ký PNG hoặc flag `boolean` | **Hạ tầng PKI X.509 PAdES nội bộ**, ký nháy số, DocMDP niêm phong PDF |
| **Kiểm định chữ ký số** | Đóng gói trong Adobe Reader hoặc phần mềm riêng | Hầu như không có cơ chế tự kiểm định API | **Tích hợp Verify Engine 7 tiêu chí** (Integrity, Cryptography, CA Chain...) |
| **Quy trình DevSecOps CI/CD** | Đóng kín (Proprietary), khách hàng không kiểm soát được mã nguồn | Thiếu sót, chỉ có CI/CD cơ bản hoặc test thủ công | **Chuẩn hóa DevSecOps toàn diện** (SAST, SCA, DAST, Secret Scan, Auto Migration) |

### 1.3.2. Phân tích khoảng trống kỹ thuật (Gap Analysis)
Từ phân tích đối chiếu trên, đề tài xác định 4 khoảng trống kỹ thuật cốt lõi cần giải quyết:
1. **Khoảng trống về bảo mật văn bản số**: Phần lớn hệ thống nội bộ tự phát triển mới chỉ dừng lại ở mức "quản lý siêu dữ liệu" (Metadata Management), chưa có giải pháp bảo vệ nội dung nhị phân (Binary Content) của tệp PDF. Tài liệu sau khi tải xuống máy trạm có thể bị sửa đổi số liệu mà hệ thống không có khả năng phát hiện.
2. **Khoảng trống về tính đồng thời trong cấp số**: Thao tác sinh số văn bản theo chu kỳ thường xuyên xảy ra xung đột khi có nhiều văn thư thực hiện phát hành văn bản vào cùng một thời điểm cao điểm, dẫn đến trùng lặp số hiệu vi phạm Nghị định 30/2020/NĐ-CP.
3. **Khoảng trống về mô hình phân quyền và cây tổ chức**: Việc quản lý danh mục phòng ban phân cấp bằng mô hình cha-con đơn thuần (`parent_id`) dẫn đến các truy vấn đệ quy phức tạp và hiệu năng suy giảm nghiêm trọng khi hệ thống có hàng chục khoa/viện và hàng trăm bộ môn trực thuộc.
4. **Khoảng trống về quy trình kiểm thử an toàn phần mềm**: Chưa có một khung quy trình chuẩn mực kết hợp giữa thực hành lập trình an toàn (Secure Coding) và tự động hóa kiểm thử an ninh (DevSecOps) để phát hiện và ngăn chặn sớm các lỗi bảo mật ngay từ giai đoạn viết mã (Source Code) đến giai đoạn triển khai (Deployment).

---

## 1.4. KHẢO SÁT QUY TRÌNH DEVSECOPS VÀ CÁC KỸ THUẬT LẬP TRÌNH AN TOÀN

### 1.4.1. Khái niệm và triết lý DevSecOps ("Shift-Left Security")
**DevSecOps** (Development - Security - Operations) là sự mở rộng tự nhiên của triết lý DevOps, đưa yếu tố bảo mật trở thành một phần không thể tách rời trong mọi giai đoạn của vòng đời phát triển phần mềm (Software Development Life Cycle - SDLC).

```
   ┌───────────────────────────────────────────────────────────────┐
   │                    VÒNG ĐỜI DEVSECOPS                         │
   │                                                               │
   │   [Plan] ──► [Code] ──► [Build] ──► [Test] ──► [Release] ──► [Deploy] ──► [Monitor]
   │     │          │          │           │           │            │            │
   │  Threat    IDE Lint/   SCA / CVE   SAST Code   Container    DAST Live    Audit Trail
   │ Modeling  Pre-commit   Dependency   Quality    Image Scan    API Scan    & Logs
   └───────────────────────────────────────────────────────────────┘
```

- **Triết lý Shift-Left (Dịch chuyển sang trái)**: Thay vì dồn việc kiểm thử thâm nhập (Penetration Testing) và đánh giá an toàn vào cuối chu kỳ dự án, DevSecOps dịch chuyển việc kiểm tra an ninh sang phía bên trái của đường ống phát triển (ngay từ lúc viết mã nguồn và đóng gói mã nguồn). Điều này giúp:
  - Phát hiện sớm lỗi logic và lỗ hổng mã hóa ngay trên máy trạm của lập trình viên hoặc qua Pull Request.
  - Giảm thiểu tối đa chi phí sửa lỗi (chi phí sửa một lỗi bảo mật ở khâu phát triển thấp hơn từ 10 đến 30 lần so với khi phần mềm đã đi vào vận hành).
  - Tự động hóa kiểm định bảo mật trong quy trình tích hợp liên tục (CI/CD Pipeline), biến bảo mật thành các tiêu chí kiểm tra tự động (Quality Gates).

### 1.4.2. Các trụ cột kỹ thuật kiểm thử an toàn trong đường ống CI/CD
1. **SAST (Static Application Security Testing - Kiểm thử tĩnh)**:
   - Phân tích mã nguồn và bytecode mà không cần thực thi ứng dụng.
   - Tìm kiếm các mẫu mã độc, cấu hình bảo mật sai, rò rỉ bộ nhớ, hoặc các lỗi lập trình không an toàn (ví dụ: hardcoded password, thiếu kiểm tra null, SQL Injection).
   - *Công cụ khảo sát*: SonarQube, SpotBugs.
2. **SCA (Software Composition Analysis - Phân tích phụ thuộc)**:
   - Kiểm tra các thư viện bên thứ ba (Dependencies) được khai báo trong tệp quản lý dự án (`pom.xml` của Maven) đối chiếu với cơ sở dữ liệu lỗ hổng bảo mật quốc gia (National Vulnerability Database - NVD).
   - Cảnh báo các thư viện chứa lỗ hổng CVE nguy hiểm (như các lỗ hổng thực thi mã từ xa RCE trong log4j, Jackson Databind...).
   - *Công cụ khảo sát*: OWASP Dependency-Check, Trivy.
3. **DAST (Dynamic Application Security Testing - Kiểm thử động)**:
   - Kiểm thử ứng dụng từ góc nhìn bên ngoài khi hệ thống đang ở trạng thái hoạt động thực tế (Black-box / Grey-box testing).
   - Mô phỏng các cuộc tấn công gửi payload qua HTTP Request để kiểm tra cơ chế phòng vệ của API: Cross-Site Scripting (XSS), SQL Injection, Header an toàn (CSP, HSTS), kiểm tra quản lý phiên (Session Management) và phân quyền xác thực.
   - *Công cụ khảo sát*: OWASP ZAP (Zed Attack Proxy).
4. **Secret Scanning (Quét dữ liệu nhạy cảm)**:
   - Tự động quét mã nguồn và lịch sử Git để ngăn chặn việc lập trình viên vô tình đẩy các thông tin nhạy cảm (Private Key, API Key, Token bí mật, Mật khẩu cơ sở dữ liệu) lên kho chứa mã nguồn.
   - *Công cụ khảo sát*: GitGuardian, Trivy Secret Scanner.

### 1.4.3. Các kỹ thuật lập trình an toàn (Secure Coding Practices) ứng dụng trong hệ thống
Để xây dựng một hệ thống backend an toàn theo chiều sâu (Defense-in-Depth), các kỹ thuật lập trình an toàn được khảo sát và áp dụng bao gồm:
- **Kiểm soát xác thực và quản lý Token an toàn**:
  - Triển khai mô hình JWT Dual-Token: Access Token có thời gian sống ngắn (Short-lived, ví dụ 15 phút) nhằm hạn chế rủi ro lộ lọt; Refresh Token được lưu trữ trong HttpOnly Cookie chống tấn công XSS.
  - Áp dụng kỹ thuật **Token Rotation**: Mỗi lần đổi token mới, Refresh Token cũ sẽ bị thu hồi ngay lập tức để phát hiện và ngăn chặn tấn công giả mạo (Replay Attack).
  - Tích hợp bộ đệm kiểm soát tần suất truy cập (**Rate Limiting Token Bucket**) nhằm ngăn chặn tấn công vét cạn mật khẩu (Brute-force Attacks) vào các API xác thực.
- **Phân quyền ma trận đa lớp (Fine-grained RBAC & Scope)**:
  - Kiểm tra thẩm quyền nghiêm ngặt tại tầng Security Filter và tầng Service. Mọi thao tác truy xuất dữ liệu nghiệp vụ đều phải đối soát qua cây tổ chức (`OrgNodeClosure`) để đảm bảo người dùng chỉ được xem/sửa tài liệu trong phạm vi đơn vị hoặc quyền hạn được ủy quyền.
- **Ngăn chặn xung đột đồng thời (Concurrency Control)**:
  - Áp dụng cơ chế **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)** tại tầng cơ sở dữ liệu khi cấp số văn bản. Đảm bảo tính toán toàn vẹn ACID, triệt tiêu hiện tượng Race Condition khi nhiều văn thư cùng cấp số văn bản vào một sổ tại cùng một mili giây.
- **An toàn lưu trữ tệp và phòng chống tấn công Path Traversal**:
  - Mã hóa định danh tệp tin vật lý bằng UUID ngẫu nhiên kết hợp làm sạch tên tệp (Sanitization).
  - Chuẩn hóa đường dẫn tuyệt đối và xác minh tệp nằm trọn vẹn trong thư mục lưu trữ hợp lệ, ngăn chặn tuyệt đối việc sử dụng đường dẫn tương đối `../` hoặc Symbolic Link để đọc trộm tệp tin hệ điều hành.
- **Kiểm toán và lưu vết bất đồng bộ (AOP Audit Logging)**:
  - Sử dụng lập trình hướng khía cạnh (AOP) để tự động bắt vết mọi hành động đọc/ghi của người dùng kèm địa chỉ IP, User-Agent và ghi nhận lịch sử biến động dữ liệu (Entity Change History).
  - Xử lý ghi nhật ký bất đồng bộ (`@Async`) trong transaction độc lập nhằm tối ưu hóa hiệu năng, không làm ảnh hưởng đến thời gian phản hồi của luồng nghiệp vụ chính.

---

## 1.5. XÁC ĐỊNH MỤC TIÊU VÀ PHẠM VI NGHIÊN CỨU CỦA ĐỀ TÀI

### 1.5.1. Mục tiêu tổng quát (General Objective)
Nghiên cứu khung quy trình DevSecOps và các kỹ thuật lập trình an toàn, ứng dụng vào việc thiết kế, phát triển và thử nghiệm hoàn chỉnh hệ thống thông tin Văn phòng điện tử (**e-Office Backend**) phục vụ công tác chỉ đạo, điều hành, luân chuyển và quản lý văn bản điện tử tại Trường Đại học Điện Lực (EPU); đảm bảo tính pháp lý, tính toàn vẹn, chống chối bỏ của văn bản thông qua giải pháp ký số chuẩn PAdES và khả năng tự động hóa kiểm thử an toàn phần mềm liên tục.

### 1.5.2. Các mục tiêu cụ thể (Specific Objectives)
Để hiện thực hóa mục tiêu tổng quát, đề tài đặt ra 4 mục tiêu cụ thể cần hoàn thành:

1. **Mục tiêu 1: Chuẩn hóa quy trình nghiệp vụ văn bản điện tử theo Nghị định 30/2020/NĐ-CP**:
   - Xây dựng mô hình cây tổ chức cơ quan linh hoạt không giới hạn độ sâu dựa trên giải pháp bảng đóng (Closure Table Pattern).
   - Thiết kế ma trận phân quyền 3 lớp (Role toàn cục, Vị trí chức vụ tại phòng ban, Cơ chế ủy quyền).
   - Số hóa toàn diện vòng đời văn bản: Soạn thảo, quản lý đa phiên bản (Document Versioning), thẩm định ký nháy, lãnh đạo phê duyệt, lưu trữ bút phê chỉ đạo, và thiết lập chuỗi văn bản liên kết (Document Lineage).
   - Thiết kế giải pháp quản lý Sổ văn bản 2 cấp (Cấp Đơn vị và Cấp Phòng/Ban) với cơ chế cấp số tự động chống trùng lặp (Pessimistic Locking) và tự động làm mới chu kỳ năm.

2. **Mục tiêu 2: Thiết kế và tích hợp hạ tầng chữ ký số PKI X.509 PAdES và Động cơ kiểm định chữ ký**:
   - Xây dựng phân hệ Quản trị chứng thư số Root CA nội bộ và tự động cấp phát cặp khóa RSA/chứng thư số X.509 cho cán bộ, giảng viên theo chuẩn mật mã an toàn.
   - Hiện thực hóa quy trình ký nháy điện tử (Digital Initialing Stamp) và ký số phát hành văn bản chính thức chuẩn PAdES trên nền tảng thư viện Apache PDFBox và BouncyCastle.
   - Áp dụng cơ chế niêm phong tài liệu DocMDP ($P=1$), khóa cứng tài liệu sau khi phát hành chống mọi hành vi sửa đổi.
   - Xây dựng **Động cơ kiểm định chữ ký số độc lập (Verify Engine)** kiểm tra toàn diện **7 tiêu chí bảo mật**: Tính toàn vẹn băm SHA-256 (`INTEGRITY`), Tính hợp lệ mật mã CMS (`CRYPTOGRAPHIC VALIDITY`), Thời hạn hiệu lực chứng thư (`VALIDITY PERIOD`), Chuỗi tin cậy CA (`CA TRUST CHAIN`), Danh sách thu hồi (`REVOCATION STATUS`), Vùng ký bao phủ toàn bộ tệp (`BYTERANGE COVERAGE`), và Phát hiện phá vỡ niêm phong (`DOCMDP MODIFICATION DETECTION`).

3. **Mục tiêu 3: Xây dựng và tích hợp đường ống DevSecOps tự động hóa (Automated CI/CD Pipeline)**:
   - Tích hợp kiểm thử bảo mật tự động vào GitHub Actions Workflow.
   - Triển khai công cụ kiểm thử tĩnh SAST (SonarQube) để rà quét mã nguồn và duy trì chuẩn chất lượng mã (Quality Gate: 0 lỗi Critical/Blocker, độ bao phủ kiểm thử Unit/Integration Test đạt trên 80%).
   - Triển khai công cụ SCA (OWASP Dependency-Check) để tự động phát hiện và ngăn chặn các thư viện bên thứ ba có lỗ hổng CVE từ mức High trở lên.
   - Triển khai kịch bản kiểm thử động DAST (OWASP ZAP) có xác thực người dùng trên môi trường Staging/Docker để phát hiện sớm các lỗ hổng thời gian thực.

4. **Mục tiêu 4: Đánh giá hiệu năng và mức độ đáp ứng an toàn của hệ thống**:
   - Đo lường và đánh giá hiệu năng của hệ thống trong các kịch bản chịu tải cao: Kiểm thử tính chính xác của cơ chế khóa cấp số văn bản khi có hàng trăm request đồng thời.
   - Đo lường thời gian thực hiện ký số và kiểm định chữ ký trên các tệp tài liệu PDF dung lượng khác nhau.
   - Đánh giá khả năng phòng vệ của hệ thống trước các kịch bản tấn công thực tế (tấn công can thiệp sửa tệp PDF sau ký, tấn công IDOR duyệt văn bản trái phép, tấn công Path Traversal khi tải tệp).

### 1.5.3. Đối tượng và phạm vi nghiên cứu
- **Đối tượng nghiên cứu (Research Subject)**:
  - Khung quy trình DevSecOps và các công cụ bảo mật tự động hóa trong CI/CD.
  - Các kỹ thuật lập trình an toàn backend trên nền tảng Java/Spring Boot và hệ quản trị cơ sở dữ liệu PostgreSQL.
  - Cơ chế mã hóa khóa công khai PKI, chứng thư số chuẩn X.509, chuẩn chữ ký số PDF PAdES và các giải pháp kiểm định tính toàn vẹn tài liệu.
  - Quy chuẩn hành chính văn thư và quy trình xử lý văn bản điện tử trong cơ quan Nhà nước và cơ sở giáo dục đại học.
- **Phạm vi nghiên cứu (Research Scope)**:
  - *Về mặt kiến trúc*: Đề tài tập trung nghiên cứu, thiết kế và hiện thực hóa toàn bộ **phân hệ Backend RESTful API** (`eoffice-be`) - nơi nắm giữ toàn bộ logic nghiệp vụ cốt lõi, cơ chế phân quyền, xử lý mã hóa dữ liệu và bảo vệ an toàn cho hệ thống.
  - *Về mặt nghiệp vụ*: Giới hạn trong phạm vi quản lý văn bản đi, văn bản chỉ đạo điều hành nội bộ, sổ văn bản 2 cấp và luồng phê duyệt khép kín theo mô hình phòng ban của Trường Đại học Điện Lực.
  - *Về mặt công nghệ*:
    - Ngôn ngữ & Framework: Java 21 LTS, Spring Boot 3.4.x, Spring Security 7.
    - Cơ sở dữ liệu: PostgreSQL 16 kết hợp Liquibase quản lý lược đồ dữ liệu.
    - Thư viện bảo mật & PDF: BouncyCastle Security Provider, Apache PDFBox 3.x, JJWT.
    - Môi trường & DevOps: Docker, Docker Compose, GitHub Actions, SonarQube, OWASP Dependency-Check, OWASP ZAP.

---

## 1.6. PHƯƠNG PHÁP NGHIÊN CỨU VÀ KẾ HOẠCH TRIỂN KHAI

### 1.6.1. Phương pháp nghiên cứu
Đề tài kết hợp chặt chẽ giữa nghiên cứu lý thuyết chuyên sâu và nghiên cứu thực nghiệm ứng dụng:

```
┌────────────────────────────────────────────────────────────────────────┐
│                      PHƯƠNG PHÁP NGHIÊN CỨU                            │
├───────────────────────────────────┬────────────────────────────────────┤
│   1. Phương pháp nghiên cứu       │  2. Phương pháp nghiên cứu         │
│             lý thuyết             │              thực nghiệm           │
├───────────────────────────────────┼────────────────────────────────────┤
│ • Thu thập văn bản quy phạm:      │ • Xây dựng mã nguồn dự án mẫu:     │
│   Nghị định 30/2020/NĐ-CP,         │   Java 21, Spring Boot, Postgres   │
│   Luật Giao dịch điện tử 2023.    │ • Thiết lập hạ tầng CA và ký số    │
│ • Nghiên cứu các chuẩn kỹ thuật:  │   bằng BouncyCastle, Apache PDFBox │
│   ETSI PAdES, ISO 32000 (PDF),    │ • Xây dựng kịch bản CI/CD Pipeline │
│   RFC 5280 (X.509 PKI).           │   tự động hóa trên GitHub Actions  │
│ • Nghiên cứu tiêu chuẩn bảo mật:  │ • Tiến hành kiểm thử Unit/Mock,    │
│   OWASP Top 10, CWE/SANS 25,      │   kiểm thử tích hợp, quét SAST/    │
│   NIST SP 800-64 (SDLC Security). │   SCA/DAST và kiểm thử tải.        │
└───────────────────────────────────┴────────────────────────────────────┘
```

1. **Phương pháp nghiên cứu lý thuyết**:
   - Nghiên cứu các quy định pháp luật hiện hành của Nhà nước về quản lý văn thư, quy trình ban hành và lưu trữ văn bản điện tử.
   - Nghiên cứu các tài liệu kỹ thuật quốc tế về kiến trúc chữ ký số trên tài liệu PDF (ISO 32000-1/2, ETSI TS 102 778 PAdES).
   - Nghiên cứu các khuyến nghị bảo mật ứng dụng web của tổ chức OWASP, phương pháp tiếp cận lập trình an toàn cho ứng dụng Java doanh nghiệp.
2. **Phương pháp nghiên cứu thực nghiệm và đánh giá**:
   - Thiết kế hệ thống theo mô hình kiến trúc phân tầng (Controller - Service - Repository - Audit).
   - Trực tiếp xây dựng mã nguồn hoàn chỉnh cho phân hệ backend với hơn 20 Use Case nghiệp vụ chính.
   - Thực nghiệm triển khai quy trình DevSecOps khép kín từ khâu lập trình trên IDE đến đóng gói Docker container và triển khai lên môi trường thử nghiệm (Staging).
   - Đánh giá định lượng kết quả thông qua báo cáo quét an ninh tự động của SonarQube (SAST), OWASP Dependency-Check (SCA), OWASP ZAP (DAST) và tỷ lệ kiểm thử thành công.

### 1.6.2. Kế hoạch và các bước thực hiện đề tài

Kế hoạch triển khai đề tài được tổ chức thành 05 giai đoạn mạch lạc:

```
Giai đoạn 1 (Tuần 1 - 2)   : Khảo sát hiện trạng, thu thập yêu cầu và xác định mục tiêu đề tài
Giai đoạn 2 (Tuần 3 - 5)   : Nghiên cứu lý thuyết DevSecOps, PKI PAdES và thiết kế kiến trúc hệ thống
Giai đoạn 3 (Tuần 6 - 10)  : Lập trình phát triển hệ thống eoffice-be và tích hợp các kỹ thuật an toàn
Giai đoạn 4 (Tuần 11 - 13) : Xây dựng CI/CD Pipeline DevSecOps, quét bảo mật SAST/SCA/DAST và tối ưu
Giai đoạn 5 (Tuần 14 - 15) : Đánh giá kết quả thực nghiệm, hoàn thiện tài liệu và viết báo cáo chuyên đề
```

- **Giai đoạn 1: Khảo sát và Xác định mục tiêu (Tuần 1 - Tuần 2)**:
  - Khảo sát quy trình nghiệp vụ văn bản tại đơn vị thực tế (EPU).
  - Xác định các bài toán tồn đọng và phân tích khoảng trống kỹ thuật (Gap Analysis).
  - Hoàn thiện bản đặc tả yêu cầu và xác định phạm vi, mục tiêu của đề tài (Nội dung Chương 1).
- **Giai đoạn 2: Thiết kế Kiến trúc và Nghiên cứu Công nghệ (Tuần 3 - Tuần 5)**:
  - Nghiên cứu cấu trúc tài liệu PDF, giải thuật ký số CMS/PKCS#7 và cơ chế niêm phong DocMDP.
  - Thiết kế cấu trúc cơ sở dữ liệu quan hệ (ERD), áp dụng mô hình Closure Table cho cây tổ chức.
  - Thiết kế kiến trúc phân tầng, đặc tả RESTful API và ma trận quyền RBAC 3 lớp.
- **Giai đoạn 3: Hiện thực hóa Hệ thống và Kỹ thuật An toàn (Tuần 6 - Tuần 10)**:
  - Khởi tạo khung dự án Spring Boot 3.4 trên nền Java 21, quản lý phiên bản cơ sở dữ liệu qua Liquibase.
  - Xây dựng tầng bảo mật: JWT Dual-token, Refresh Token Rotation, Rate Limiting, Password Hashing.
  - Hiện thực hóa các phân hệ nghiệp vụ: Quản lý người dùng, cây tổ chức, sổ văn bản 2 cấp, soạn thảo dự thảo, quản lý phiên bản tài liệu.
  - Phát triển module ký số PKI X.509 PAdES (ký nháy, ký phát hành, DocMDP) và Động cơ kiểm định 7 tiêu chí.
  - Tích hợp hệ thống AOP Audit Log và cơ chế khóa bi quan (Pessimistic Lock) khi cấp số văn bản.
- **Giai đoạn 4: Tích hợp DevSecOps Pipeline và Đánh giá An ninh (Tuần 11 - Tuần 13)**:
  - Viết kịch bản tự động hóa CI/CD với GitHub Actions.
  - Cấu hình SonarQube phân tích chất lượng mã nguồn và bảo mật tĩnh (SAST).
  - Tích hợp OWASP Dependency-Check kiểm soát lỗ hổng thư viện bên thứ ba (SCA).
  - Thiết lập môi trường Docker Staging, chạy quét động DAST bằng OWASP ZAP với tài khoản xác thực.
  - Khắc phục triệt để các cảnh báo bảo mật được công cụ ghi nhận.
- **Giai đoạn 5: Đánh giá Thực nghiệm và Tổng kết Đề tài (Tuần 14 - Tuần 15)**:
  - Kiểm thử tải và kiểm thử tính toàn vẹn tài liệu sau khi ký phát hành.
  - Tổng hợp số liệu kiểm thử, kết quả quét an ninh và đánh giá mức độ hoàn thành so với mục tiêu đề ra.
  - Hoàn thiện toàn bộ tài liệu báo cáo chuyên đề đồ án tốt nghiệp và chuẩn bị báo cáo trước Hội đồng.

---

## 1.7. KẾT LUẬN CHƯƠNG 1

Chương 1 đã trình bày một cách toàn diện và sâu sắc bức tranh tổng quan về đề tài:
1. Đã chỉ rõ tính cấp thiết của việc hiện đại hóa công tác quản trị hành chính trên môi trường số gắn liền với bài toán bảo mật ứng dụng theo quy chuẩn của Nghị định 30/2020/NĐ-CP và Luật Giao dịch điện tử.
2. Đã khảo sát chi tiết hiện trạng nghiệp vụ tại Trường Đại học Điện Lực (EPU), nhận diện các bất cập của phương thức quản lý truyền thống cũng như các hạn chế của các phần mềm hiện có trên thị trường.
3. Đã làm rõ 4 điểm lệch kỹ thuật cốt lõi (Gaps) về bảo vệ tệp tài liệu số, xung đột cấp số văn bản, quản trị cây tổ chức động và sự thiếu vắng của quy trình kiểm thử bảo mật tự động.
4. Đã phân tích triết lý DevSecOps ("Shift-Left") cùng các công cụ kiểm thử an toàn tự động (SAST, SCA, DAST) và các kỹ thuật lập trình an toàn chuyên sâu trong môi trường Java/Spring Boot.
5. Đã xác định rõ ràng đối tượng, phạm vi nghiên cứu, mục tiêu tổng quát, 4 mục tiêu cụ thể và phương pháp luận triển khai có tính khả thi cao.

Toàn bộ các nội dung khảo sát và mục tiêu được xác định trong Chương 1 là tiền đề vững chắc để triển khai các nội dung nghiên cứu lý thuyết chuyên sâu, thiết kế kiến trúc hệ thống và xây dựng giải pháp kỹ thuật an toàn trong **Chương 2** tiếp theo.

---

*(Tài liệu chuyên đề kỹ thuật - Hệ thống e-Office Trường Đại học Điện Lực - Phân hệ Backend eoffice-be)*

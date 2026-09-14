# 📝 Nhóm Chức Năng 3: Soạn Thảo & Phê Duyệt Văn Bản (UC08 - UC14)

Đây là nhóm chức năng **trọng tâm cốt lõi nhất** của hệ thống E-Office, điều phối toàn bộ quy trình từ lúc chuyên viên tạo dự thảo đến khi lãnh đạo duyệt và ký số/phát hành.

---

## 1. Chi Tiết Từng Use Case

### 1.1. UC08: Tạo dự thảo văn bản
- **Tác nhân:** Người soạn thảo.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Chọn loại văn bản, đơn vị ban hành, người ký dự kiến`.
    - `[include] Nhập thông tin: Trích yếu, Nội dung tóm tắt, Nơi nhận, Ngày hiệu lực dự kiến, Độ mật, Độ khẩn`.
    - `[include] Khởi tạo trạng thái dự thảo (DRAFT)`.
    - `[include] Lưu dự thảo / Lưu nháp`.
    - `[extend] Hủy tạo dự thảo`.
  - 🎯 **Theo core của thầy:**
    - **Văn bản liên kết (Parent/Child Document):** Khi tạo dự thảo, cho phép chọn "Căn cứ theo văn bản" (ví dụ: Kế hoạch triển khai căn cứ theo Quyết định số 125/QĐ-ĐHEPU). Bổ sung trường `parent_document_id` vào Entity `Document`.
    - **Nơi nhận và lưu:** Theo đặc tả đề tài, văn bản phải có trường "Nơi nhận và lưu" — cần có trường `recipients_text` (văn bản) và liên kết `document_distributions` (danh sách đơn vị nhận chính thức).
    - **Thông tin định danh đầy đủ theo mẫu văn bản hành chính:** Số hiệu, Loại văn bản, Ngày có hiệu lực, Nội dung tóm tắt.

---

### 1.2. UC09: Tải file DOCX/PDF
- **Tác nhân:** Người soạn thảo.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Chọn tệp & Kiểm tra định dạng` (chỉ cho phép `.pdf`, `.docx`, `.doc`, `.xlsx`, `.zip`).
    - `[include] Kiểm tra dung lượng tệp` (tối đa 25MB).
    - `[include] Lưu tệp vào Storage` (Local hoặc MinIO/S3).
    - `[include] Liên kết tệp đính kèm với dự thảo văn bản`.
    - `[extend] Báo lỗi tệp không hợp lệ & Tải tài liệu đính kèm (Download)`.
  - 🎯 **Theo core của thầy (Bảo mật lưu trữ tệp - DevSecOps):**
    - **Kiểm tra MIME Type thực sự (Magic Bytes):** Không chỉ kiểm tra đuôi file mà phải kiểm tra Content-Type thực bằng thư viện `Apache Tika` để chặn tấn công file giả mạo đuôi (`.exe` đổi tên thành `.pdf`).
    - **Mã hóa tệp nhạy cảm lưu trữ:** File văn bản có độ mật cao nên mã hóa AES-256 trước khi lưu vào Storage (MinIO SSE-S3 hoặc SSE-C).
    - **Tên file lưu trữ phải được randomize:** Không lưu theo tên gốc (tránh lộ thông tin, tránh path traversal). Dùng UUID làm tên file thực trong Storage.

---

### 1.3. UC10: Chỉnh sửa và lưu phiên bản
- **Tác nhân:** Người soạn thảo.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Mở dự thảo & chỉnh sửa nội dung/metadata`.
    - `[include] Cập nhật tệp & Tự động tăng số phiên bản` (Version 1.0 → 1.1 → 2.0).
    - `[extend] Khôi phục phiên bản cũ` (Rollback version).
    - `[extend] Xóa tệp đính kèm thừa`.
  - 🎯 **Theo core của thầy:**
    - **Lưu vết chỉnh sửa theo các cấp:** Mỗi lần lưu phiên bản mới cần ghi `DocumentVersion` với thông tin: người sửa, thời gian, tóm tắt thay đổi. Đây là yếu tố *"lưu vết theo các cấp"* theo yêu cầu đề tài.
    - **Khóa chỉnh sửa khi đang trong luồng duyệt:** Sau khi người soạn thảo gửi duyệt (UC11), văn bản phải bị khóa chỉnh sửa. Chỉ mở lại khi người duyệt trả về yêu cầu chỉnh sửa.

---

### 1.4. UC11: Gửi văn bản để duyệt
- **Tác nhân:** Người soạn thảo.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Kiểm tra tính hợp lệ của hồ sơ` (đã có tệp đính kèm chính, đã chọn người duyệt, đã nhập trích yếu).
    - `[include] Chuyển trạng thái sang SUBMITTED / PENDING_REVIEW`.
    - `[include] Ghi nhận timeline & thời gian gửi`.
    - `[extend] Báo lỗi thiếu thông tin`.
    - `[extend] Tự động bắn thông báo / email tới người duyệt` (UC20).
    - `[extend] Thu hồi yêu cầu duyệt` (khi người duyệt chưa xử lý).
  - 🎯 **Theo core của thầy:**
    - **Luồng duyệt linh hoạt theo cơ cấu tổ chức:** Khi người dùng gửi duyệt, hệ thống phải xác định người duyệt tiếp theo dựa trên `PositionAssignment` và cây `OrgNodeClosure` của đơn vị ban hành — không hardcode luồng cố định.
    - **Khi thay đổi cơ cấu tổ chức:** Luồng duyệt của các văn bản đang xử lý không bị ảnh hưởng hồi tố (các văn bản đã trong luồng giữ nguyên người duyệt đã chỉ định). Chỉ văn bản mới tạo sau đó mới áp dụng cơ cấu mới.

---

### 1.5. UC12: Duyệt hoặc yêu cầu chỉnh sửa
- **Tác nhân:** Người duyệt / Ký nháy.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Xem nội dung văn bản, lịch sử phiên bản, tệp đính kèm trực tiếp`.
    - `[include] Ghi nhận xét, ý kiến chỉ đạo`.
    - `[extend] Chấp thuận văn bản` → Chuyển bước tiếp theo.
    - `[extend] Trả lại người soạn thảo / Yêu cầu chỉnh sửa` (trạng thái NEEDS_REVISION).
    - `[extend] Chuyển người ký nháy tiếp theo trong luồng`.
  - 🎯 **Theo core của thầy (Bút phê lãnh đạo & Lưu vết điều hành):**
    - **Ý kiến chỉ đạo / Bút phê lãnh đạo (Leadership Endorsement):** Khi duyệt, lãnh đạo có thể nhập ý kiến chỉ đạo cụ thể (ví dụ: *"Giao Phòng Đào tạo chủ trì thực hiện, hoàn thành trước 30/09"*) và chỉ định đơn vị/phòng ban chủ trì. Lưu vào `document_workflow_logs.leadership_instruction` và `assigned_org_node_id`.
    - **Lưu vết theo các cấp:** Mọi thao tác Duyệt/Trả lại/Yêu cầu chỉnh sửa đều được ghi vào `DocumentWorkflowLog` với đầy đủ: người thực hiện, thời gian, từ trạng thái nào sang trạng thái nào, nhận xét.

---

### 1.6. UC13: Ký nháy văn bản
- **Tác nhân:** Người duyệt / Ký nháy.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Xem nội dung văn bản`.
    - `[extend] Xác nhận ký nháy`.
    - `[extend] Góp ý / Yêu cầu chỉnh sửa`.
  - 🎯 **Theo core của thầy (Ký số thực sự trên PDF - DevSecOps):**
    - **Nhúng Digital Stamp (Dấu ký nháy điện tử) vào file PDF:**
      1. Backend lấy file PDF hiện tại từ Storage.
      2. Tạo hình ảnh Stamp gồm: Họ tên người ký nháy + Chức danh + Thời gian ký + Ảnh chữ ký mẫu (`signature_image_url`).
      3. Nhúng Stamp vào góc trên hoặc cuối trang PDF tại vị trí quy định.
      4. Băm nội dung PDF sau khi nhúng Stamp bằng `SHA-256` và lưu hash vào `document_workflow_logs.signature_hash`.
      5. Lưu bản PDF mới (có dấu ký nháy) lại vào Storage.
    - **Thư viện đề xuất:** `Apache PDFBox` (miễn phí, open-source, tích hợp tốt với Spring Boot).

---

### 1.7. UC14: Phê duyệt và ký phát hành
- **Tác nhân:** Người ký phát hành (Ban Giám Hiệu, Thủ trưởng đơn vị).
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Xem toàn bộ hồ sơ dự thảo, danh sách người đã ký nháy`.
    - `[extend] Phê duyệt văn bản chính thức`.
    - `[extend] Ký phát hành văn bản`.
    - `[extend] Từ chối phê duyệt` (kèm lý do).
  - 🎯 **Theo core của thầy (Ký số PKI & Chống giả mạo - Trọng tâm DevSecOps):**
    - **Ký số điện tử chính thức lên bản PDF cuối cùng:**
      1. Người ký phát hành bấm "Ký phát hành" → Backend tổng hợp bản PDF cuối (bao gồm đầy đủ nội dung văn bản + số hiệu chính thức + ngày ban hành).
      2. Nhúng chữ ký số X.509 của người ký phát hành bằng Private Key (lưu trong Keystore / Mock Certificate).
      3. Đặt cờ **`certificationLevel = CERTIFIED_NO_CHANGES_ALLOWED`** trên PDF → Mọi sửa đổi sau này đều bị phát hiện và chữ ký mất hiệu lực tự động.
      4. Lưu `official_hash` (SHA-256 của file PDF chính thức cuối) vào `documents.official_hash`.
    - **Tách biệt file nháp và file chính thức:** File PDF chính thức được lưu vào đường dẫn Storage riêng biệt, không ghi đè lên file nháp. File chính thức không được phép xóa/sửa từ bất kỳ API nào.

---

## 2. Hướng Dẫn Kỹ Thuật Hoàn Thiện Nhóm Chức Năng

### Bước 1: Thiết kế Entities cốt lõi
1. **Entity `Document` (`documents`):** `id`, `title` (trích yếu), `document_type_id`, `issuing_org_id`, `drafter_id`, `signer_id`, `urgency_level`, `security_level`, `status`, `parent_document_id` (văn bản căn cứ), `recipients_text` (nơi nhận), `official_number`, `issue_date`, `effective_date`, `official_hash` (SHA-256 file chính thức).
2. **Entity `DocumentVersion` (`document_versions`):** `id`, `document_id`, `version_number`, `file_url`, `file_name`, `file_size`, `change_summary`, `created_by`.
3. **Entity `DocumentAttachment` (`document_attachments`):** `id`, `document_id`, `version_id`, `file_name`, `stored_file_name` (UUID), `content_type`, `file_size`, `is_primary`.
4. **Entity `DocumentWorkflowLog` (`document_workflow_logs`):** `id`, `document_id`, `action`, `actor_id`, `from_status`, `to_status`, `comment`, `leadership_instruction`, `assigned_org_node_id`, `signature_hash`, `created_date`.

### Bước 2: Xây dựng File Storage Service (Bảo mật - DevSecOps)
```java
public interface FileStorageService {
    String storeFile(MultipartFile file, String folder);   // Trả về stored_file_name (UUID)
    Resource loadFileAsResource(String storedFileName);
    void deleteFile(String storedFileName);
    String computeFileSha256(String storedFileName);       // Tính hash để verify
}
```

### Bước 3: Workflow State Machine
```
DRAFT ──(Submit)──► SUBMITTED ──(Assign Reviewer)──► IN_REVIEW
    ◄──(Withdraw)──       ◄──────(Return for Revision)──────┘
                                        │
                              (Initial Sign/Approve)
                                        │
                                  LEADER_REVIEW
                                        │
                              (Sign & Publish Approve)
                                        │
                                    APPROVED ──(Issue Number)──► PUBLISHED
                                        │
                               (Reject)─┴──► REJECTED
```

### Bước 4 (Theo core thầy): Tích hợp Apache PDFBox để Ký số
```xml
<!-- Thêm vào pom.xml -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.2</version>
</dependency>
```

# 🎯 Hướng Dẫn Chuẩn Hóa Theo Core Đề Tài (DevSecOps & Hệ Điều Hành Tác Nghiệp)

> **Tên đề tài:** Nghiên cứu quy trình DevSecOps và các kỹ thuật lập trình an toàn ứng dụng trong phát triển thử nghiệm hệ thống thông tin hỗ trợ điều hành các hoạt động của một đơn vị trên văn bản điện tử.

Tài liệu này tổng hợp **toàn bộ các điểm lệch (Gap)** giữa thiết kế Use Case hiện tại và yêu cầu cốt lõi của đề tài, kèm hướng dẫn bổ sung/hiệu chỉnh cụ thể vào từng Use Case.

---

## 📋 Ma Trận Tổng Hợp Hiện Trạng vs. Yêu Cầu Core Đề Tài

| Nhóm chức năng | Use Case | Mô tả chức năng | Tình trạng hiện tại | Chi tiết thiếu / Cần triển khai |
|:---|:---:|:---|:---:|:---|
| **1. Tài Khoản & Tổ Chức** | **UC01** | Đăng nhập & mở rộng | 🟡 70% | Đã có: Login, Refresh Token, Đổi mật khẩu.<br>⚠️ Thiếu: Register, Quên MK/OTP Email, Đăng xuất (Revoke token) |
| | **UC02** | Quản lý tài khoản người dùng | 🔴 Chưa làm API | Mới có Entity/DB (`user_logins`, `user_infos`).<br>⚠️ Thiếu: `UserController` / `UserService` (CRUD, Tìm kiếm, Khóa/Mở khóa) |
| | **UC03** | Quản lý đơn vị trực thuộc | 🟢 85% | Đã có `OrgNodeController`, `OrgNodeService`, `org_node_closures`.<br>⚠️ Cần hoàn thiện: Re-parenting cây & đồng bộ luồng duyệt |
| | **UC04** | Gán vai trò & phân quyền | 🟡 50% | Đã có Entity & `PermissionResolver`.<br>⚠️ Thiếu: `PositionAssignmentController`, gán quyền chức danh |
| **2. Danh Mục & Sổ VB** | **UC05** | Quản lý loại văn bản | 🟢 100% | Đã có `DictionaryItemController` (`DOCUMENT_TYPE`, `POSITION`,...) |
| | **UC06** | Quản lý sổ văn bản | 🔴 Chưa có | ⚠️ Thiếu: Entity `DocumentBook` 2 cấp, Service & Controller |
| | **UC07** | Cấp số văn bản | 🔴 Chưa có | ⚠️ Thiếu: Logic sinh số nhảy, Cronjob reset năm mới, kiểm tra điều kiện |
| **3. Soạn Thảo & Phê Duyệt** | **UC08** | Tạo dự thảo văn bản | 🔴 Chưa có | ⚠️ Thiếu: Entity `Document`, Controller & Service tạo/lưu dự thảo |
| | **UC09** | Tải file DOCX/PDF | 🔴 Chưa có | ⚠️ Thiếu: File Storage Service, `DocumentAttachment`, API Upload/Validate |
| | **UC10** | Chỉnh sửa & lưu phiên bản | 🔴 Chưa có | ⚠️ Thiếu: `DocumentVersion`, cơ chế versioning & rollback |
| | **UC11** | Gửi văn bản để duyệt | 🔴 Chưa có | ⚠️ Thiếu: Workflow State Machine, timeline, thu hồi yêu cầu |
| | **UC12** | Duyệt hoặc yêu cầu chỉnh sửa | 🔴 Chưa có | ⚠️ Thiếu: `WorkflowLog`, API phê duyệt/từ chối/trả lại kèm lý do |
| | **UC13** | Ký nháy văn bản | 🔴 Chưa có | ⚠️ Thiếu: Cơ chế ký nháy điện tử, Digital Stamp trên PDF |
| | **UC14** | Phê duyệt và ký phát hành | 🔴 Chưa có | ⚠️ Thiếu: Luồng phê duyệt cấp cao, Tích hợp PKI / Ký số PDF thực sự |
| **4. Phát Hành & Theo Dõi** | **UC15** | Phát hành văn bản | 🔴 Chưa có | ⚠️ Thiếu: Module phát hành, gán số chính thức, chuyển trạng thái RELEASED |
| | **UC16** | Xem văn bản đã phát hành | 🔴 Chưa có | ⚠️ Thiếu: API danh sách/chi tiết văn bản theo phạm vi quyền |
| | **UC17** | Theo dõi tiến trình / trạng thái | 🔴 Chưa có | ⚠️ Thiếu: API timeline/tiến trình luân chuyển văn bản |
| **5. Tra Cứu & Lưu Vết** | **UC18** | Tìm kiếm, xem chi tiết & lịch sử | 🔴 Chưa có | ⚠️ Thiếu: API Search đa tiêu chí (Specification, Full-text) |
| | **UC19** | Quản lý Audit Log | 🟡 40% | Đã có: JPA Auditing, AOP.<br>⚠️ Thiếu: `AuditLog` Entity, Controller, Xuất báo cáo |
| | **UC20** | Quản lý thông báo | 🔴 Chưa có | ⚠️ Thiếu: `Notification` Entity, Email Service Async, In-app API |

---

## 🔴 ĐIỂM 1: Chữ Ký Số & Bảo Mật Văn Bản (DevSecOps & Secure Coding)

> **Ảnh hưởng đến:** UC13 (Ký nháy), UC14 (Ký phát hành), UC15 (Phát hành)

### Vấn đề lệch hiện tại:
- UC13, UC14 hiện mới ở mức ghi nhận **trạng thái boolean / text note** trong DB, **chưa có ký số thực sự** trên nội dung văn bản PDF.
- Tên đề tài nhấn mạnh: *"Xác thực và Bảo mật văn bản chỉ đạo bằng Chữ ký số"* và *"Chống sao chép, chỉnh sửa văn bản sau khi đã được phê duyệt, ký số và phát hành"*.

### Hướng dẫn bổ sung vào UC13 & UC14:

#### A. Tích hợp Ký số điện tử (PKI / X.509 / PDF Signature)
```
Thư viện đề xuất:
- Apache PDFBox (open-source, dễ tích hợp Spring Boot)
- iText 7 Community (AGPL license)
- Sử dụng Mock Certificate (tự ký - Self-signed Certificate) cho môi trường thử nghiệm
```
**Luồng ký số cho UC13 (Ký nháy):**
1. Người duyệt bấm "Ký nháy" → Backend lấy file PDF từ Storage.
2. Băm nội dung PDF bằng `SHA-256`.
3. Ký hash bằng Private Key của người duyệt (lưu trong Keystore hoặc HSM giả lập).
4. Nhúng chữ ký điện tử (digital stamp) + họ tên + thời gian vào một vùng hình ảnh trên trang cuối PDF.
5. Lưu bản PDF mới đã ký, cập nhật `signature_hash` vào bảng `document_workflow_logs`.

**Luồng ký số cho UC14 (Ký phát hành):**
1. Người ký phát hành → Backend tạo **bản PDF chính thức cuối cùng**:
   - Đóng dấu số hiệu văn bản, ngày ban hành.
   - Nhúng chữ ký số của người ký phát hành.
   - **Lock & Protect file:** Đặt cờ `certificationLevel = CERTIFIED_NO_CHANGES_ALLOWED` trong PDF → Mọi sửa đổi sau này đều bị phát hiện và chữ ký mất hiệu lực.
2. Lưu file PDF chính thức vào `Storage` với đường dẫn riêng biệt (không ghi đè bản nháp).
3. Lưu `document_hash` (SHA-256 của file PDF cuối) vào bảng `documents.official_hash`.

#### B. Cơ chế Tamper-proofing & Integrity Verification
Thêm API kiểm tra tính toàn vẹn: `GET /api/v1/documents/{id}/verify-integrity`:
- Tải file PDF chính thức từ Storage.
- Băm lại SHA-256.
- So sánh với `official_hash` đã lưu trong DB.
- Trả về: `{ "valid": true/false, "hash_stored": "...", "hash_computed": "..." }`.

#### C. DevSecOps - Các điểm cần ghi rõ trong báo cáo đề tài:
| Kỹ thuật | Triển khai trong dự án |
|:---|:---|
| **SAST (Static Analysis)** | Cấu hình SonarQube (đã có `sonar-project.properties`) - cần chạy kiểm tra và fix các issue Critical/Blocker |
| **Dependency Check** | Tích hợp `OWASP Dependency-Check Maven Plugin` vào `pom.xml` để quét lỗ hổng thư viện |
| **Mã hóa lưu trữ** | File PDF nhạy cảm mã hóa AES-256 trước khi lưu vào Storage (MinIO có hỗ trợ SSE) |
| **Audit Trail** | Mọi thao tác đọc/ghi văn bản quan trọng đều ghi `AuditLog` kèm IP, User-Agent |
| **Rate Limiting** | Bảo vệ API đăng nhập chống Brute-force (đã có `failed_login_attempts`) |
| **JWT Security** | Short-lived Access Token (15 phút) + Refresh Token HttpOnly Cookie (đã triển khai) |

---

## 🟡 ĐIỂM 2: Sổ Văn Bản 2 Cấp (UC06, UC07, UC15)

> **Ảnh hưởng đến:** UC06 (Quản lý sổ), UC07 (Cấp số), UC15 (Phát hành)

### Vấn đề lệch hiện tại:
Thiết kế `DocumentBook` chưa phản ánh đúng đặc tả:
- *"Sổ Văn bản có **02 cấp**: Cấp 1 (của Đơn vị/Trường) và Cấp 2 (của Phòng/Ban)"*.
- Mỗi loại văn bản có sổ riêng.
- Số văn bản reset về 0 vào 01/01 hàng năm.

### Hướng dẫn hiệu chỉnh Entity `DocumentBook`:
```java
// Cần bổ sung các trường sau vào DocumentBook:
@Column(name = "book_level", nullable = false)
private Integer bookLevel; // 1: Cấp Đơn vị/Trường, 2: Cấp Phòng/Ban

@Column(name = "document_type_id", nullable = false)
private Long documentTypeId; // Mỗi loại văn bản có sổ riêng (FK -> dictionary_items)

// numberPattern ví dụ:
// Cấp 1: "{NUM}/QĐ-ĐHEPU"  -> "125/QĐ-ĐHEPU"
// Cấp 2: "{NUM}/TB-CNTT"   -> "45/TB-CNTT"
```

### Quy tắc sinh số chuẩn theo đặc tả:
```
Số cấp 1 (Đơn vị Trường ban hành):   125/QĐ-ĐHEPU
Số cấp 2 (Nội bộ Phòng/Khoa ban hành): 45/TB-CNTT

Format: {STT}/{NĂM (tùy chọn)}/{MÃ_LOẠI_VB}-{MÃ_ĐƠN_VỊ}
```

### Hướng dẫn bổ sung Cronjob reset số về 0 ngày 01/01:
```java
@Component
@RequiredArgsConstructor
public class DocumentBookYearResetJob {
    private final DocumentBookRepository documentBookRepository;

    @Scheduled(cron = "0 0 0 1 1 *") // Chạy vào 00:00:00 ngày 01 tháng 01 hàng năm
    @Transactional
    public void resetAllBookCounters() {
        int currentYear = LocalDate.now().getYear();
        // Tạo bộ sổ mới cho năm mới (không xóa sổ cũ - bảo toàn lịch sử)
        documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse()
            .forEach(book -> {
                DocumentBook newBook = book.cloneForNewYear(currentYear);
                documentBookRepository.save(newBook);
                book.setIsClosed(true); // Đóng sổ năm cũ
                documentBookRepository.save(book);
            });
    }
}
```

---

## 🟢 ĐIỂM 3: Cây Tổ Chức Đa Tầng & Luồng Điều Hành Tự Động (UC03, UC04)

> **Ảnh hưởng đến:** UC03 (Quản lý đơn vị), UC04 (Phân quyền), UC11-UC14 (Luồng duyệt)

### Điểm khớp tốt với đề tài (Thế mạnh của dự án):
- `OrgNodeClosure` (Ancestor/Descendant) hỗ trợ cây đa tầng không giới hạn.
- `PermissionResolver` 3 lớp phân quyền động theo chức vụ.
- `PositionAssignment` cho phép 1 người kiêm nhiều chức vụ ở các cấp khác nhau.

### Hướng dẫn bổ sung Re-parenting API (Di chuyển nhánh cây):
Bổ sung chức năng sau vào `OrgNodeServiceImpl`:
```java
@Transactional
public List<OrgNodeResponse> moveNodeToNewParent(Long nodeId, Long newParentId) {
    // 1. Kiểm tra vòng lặp (Anti-cycle check)
    if (newParentId != null && isAncestorOf(nodeId, newParentId)) {
        throw new BusinessException(ErrorCode.ORG_NODE_CYCLE);
    }
    // 2. Xóa toàn bộ ancestor cũ của nhánh con nodeId và hậu duệ của nó
    orgNodeClosureRepository.deleteAncestorsOfSubtree(nodeId);
    // 3. Tạo lại toàn bộ ancestor mới theo newParentId
    if (newParentId != null) {
        orgNodeClosureRepository.insertAncestorsForSubtree(nodeId, newParentId);
    }
    // 4. Cập nhật parent trực tiếp
    OrgNode node = orgNodeRepository.findByIdAndDeleteFlgFalse(nodeId).orElseThrow();
    OrgNode newParent = newParentId != null
        ? orgNodeRepository.findByIdAndDeleteFlgFalse(newParentId).orElseThrow() : null;
    node.setParent(newParent);
    orgNodeRepository.save(node);
    // -> Sau bước này: toàn bộ PermissionResolver tự động phản ánh cấu trúc mới
    //    vì PermissionResolver query real-time từ org_node_closures
    return getAll(new OrgNodeFilterRequest());
}
```
> **Lưu ý quan trọng:** Vì `PermissionResolver` luôn query **real-time từ DB** (không cache cứng trong JWT), khi cây tổ chức thay đổi, luồng phê duyệt và quyền hạn của **tất cả nhân sự thuộc nhánh di chuyển** sẽ tự động cập nhật ngay lập tức mà không cần đăng xuất/đăng nhập lại.

---

## 🟡 ĐIỂM 4: Văn Bản Điều Hành & Luồng Chỉ Đạo Lãnh Đạo (UC08 - UC14)

> **Ảnh hưởng đến:** UC12 (Duyệt), UC14 (Ký phát hành), UC11 (Gửi duyệt)

### Vấn đề lệch hiện tại:
Yêu cầu đề tài: *"Cho phép chỉ đạo, điều hành dựa trên văn bản và lưu vết theo các cấp"* — chưa được thể hiện đủ trong Use Case.

### Cần bổ sung 2 chức năng đặc thù vào UC12 & UC14:

#### A. Ý kiến chỉ đạo / Bút phê lãnh đạo (Leadership Endorsement/Instruction)
Khi lãnh đạo phê duyệt, ngoài việc Approve/Reject, cần có thêm:
- **Trường `leadership_instruction`** trong `DocumentWorkflowLog`: Ghi lại ý kiến chỉ đạo cụ thể của lãnh đạo (ví dụ: *"Giao Phòng Đào tạo chủ trì triển khai, hoàn thành trước 30/09/2026"*).
- **Trường `assigned_org_node_id`**: Lãnh đạo có thể chỉ định đơn vị/phòng ban chủ trì thực hiện văn bản.
- API: `POST /api/v1/documents/{id}/leadership-instruction` (Riêng biệt với API phê duyệt).

```java
// Bổ sung vào DocumentWorkflowLog entity:
@Column(name = "leadership_instruction", columnDefinition = "TEXT")
private String leadershipInstruction; // Bút phê / Ý kiến chỉ đạo của lãnh đạo

@Column(name = "assigned_org_node_id")
private Long assignedOrgNodeId; // Đơn vị được giao chủ trì thực hiện
```

#### B. Văn bản liên kết (Parent/Child Document - Document Lineage)
Cho phép tạo văn bản mới **căn cứ theo** một văn bản cấp trên đã ban hành:
```java
// Bổ sung vào Document entity:
@Column(name = "parent_document_id")
private Long parentDocumentId; // Null nếu là văn bản gốc
// Ví dụ: Kế hoạch triển khai QĐ-125 -> parentDocumentId = ID của QĐ-125
```
- API: `GET /api/v1/documents/{id}/children` (Lấy các văn bản phái sinh từ văn bản này).
- Hiển thị chuỗi liên kết văn bản trong trang chi tiết: `QĐ-125 → KH-45 → BC-12`.

---

## 🗂️ Ánh Xạ Hiệu Chỉnh Sang Từng File Tài Liệu

| File tài liệu | Use Case ảnh hưởng | Nội dung cần bổ sung |
|:---|:---|:---|
| [01_tai-khoan-va-to-chuc.md](file:///d:/Visual%20studio%20code/eoffice-be/docs/feature-gap-analysis/01_tai-khoan-va-to-chuc.md) | UC01, UC03, UC04 | Re-parenting cây, Cơ chế ký số gắn với người dùng |
| [02_danh-muc-va-so-van-ban.md](file:///d:/Visual%20studio%20code/eoffice-be/docs/feature-gap-analysis/02_danh-muc-va-so-van-ban.md) | UC06, UC07 | `book_level` 2 cấp, Cronjob reset năm mới |
| [03_soan-thao-va-phe-duyet.md](file:///d:/Visual%20studio%20code/eoffice-be/docs/feature-gap-analysis/03_soan-thao-va-phe-duyet.md) | UC08-UC14 | Ký số PDF, Bút phê lãnh đạo, Văn bản liên kết, Tamper-proofing |
| [04_phat-hanh-va-theo-doi.md](file:///d:/Visual%20studio%20code/eoffice-be/docs/feature-gap-analysis/04_phat-hanh-va-theo-doi.md) | UC15, UC16, UC17 | PDF chính thức có chữ ký, Verify hash, `CERTIFIED_NO_CHANGES_ALLOWED` |
| [05_tra-cuu-audit-va-thong-bao.md](file:///d:/Visual%20studio%20code/eoffice-be/docs/feature-gap-analysis/05_tra-cuu-audit-va-thong-bao.md) | UC18, UC19, UC20 | DevSecOps AuditLog chi tiết (IP, User-Agent, diff), SonarQube, OWASP |

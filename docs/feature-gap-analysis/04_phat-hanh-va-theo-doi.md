# 🚀 Nhóm Chức Năng 4: Phát Hành & Theo Dõi Văn Bản (UC15 - UC17)

Tài liệu này chi tiết hóa hiện trạng, các thành phần còn thiếu và hướng dẫn kỹ thuật triển khai cho nhóm **Phát Hành & Theo Dõi Văn Bản**.

---

## 1. Chi Tiết Từng Use Case

### 1.1. UC15: Phát hành văn bản
- **Tác nhân:** Cán bộ văn thư.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Cấp số văn bản`: Tự động lấy số tiếp theo từ sổ văn bản đi tương ứng (UC07).
    - `[include] Ghi nhận ngày ban hành và ngày có hiệu lực`.
    - `[include] Xác định phạm vi phát hành` (Toàn Đơn vị, Danh sách Phòng/Ban nội bộ, Cơ quan bên ngoài).
    - `[include] Gửi phát hành` (Chuyển trạng thái sang `PUBLISHED`).
    - `[extend] Lưu hồ sơ phát hành` (tạo bản ghi lưu trữ dài hạn).
  - 🎯 **Theo core của thầy (Xác thực & Chống sửa đổi sau phát hành - Trọng tâm DevSecOps):**
    - **Tạo bản PDF chính thức có chữ ký số:** Khi phát hành, hệ thống phải kết hợp: Nội dung văn bản đã được ký số ở UC14 + Số hiệu chính thức vừa cấp + Ngày ban hành + Con dấu điện tử của Đơn vị → Tạo ra **bản PDF chính thức cuối cùng** không thể sửa đổi.
    - **Đóng dấu `CERTIFIED_NO_CHANGES_ALLOWED`:** PDF chính thức được đặt cờ chứng nhận chống chỉnh sửa. Mọi thay đổi dù nhỏ nhất vào file PDF sau khi phát hành sẽ làm vô hiệu chữ ký số, hệ thống phát hiện ngay khi verify.
    - **Lưu `official_hash` (SHA-256):** Sau khi tạo PDF chính thức, tính hash SHA-256 và lưu vào `documents.official_hash`. Đây là dấu vân tay kỹ thuật số của văn bản, phục vụ xác minh tính toàn vẹn bất kỳ lúc nào.
    - **Phân quyền xem theo phạm vi phát hành:** Văn bản chỉ hiển thị cho đúng đối tượng được chỉ định nhận (theo `DocumentDistribution`), không public toàn bộ.

---

### 1.2. UC16: Xem văn bản đã phát hành
- **Tác nhân:** Người dùng hệ thống.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[extend] Tra cứu văn bản đã ban hành` (lọc theo phạm vi quyền được xem).
    - `[extend] Xem nội dung trích yếu, người ký, số hiệu, ngày ban hành`.
    - `[extend] Tải tài liệu đính kèm chính thức` (bản PDF có chữ ký số / con dấu).
  - 🎯 **Theo core của thầy:**
    - **Kiểm tra quyền xem theo phạm vi phát hành (`DocumentDistribution`):** Người dùng chỉ thấy và tải được các văn bản mà đơn vị của họ nằm trong danh sách nhận (hoặc văn bản phát hành toàn Đơn vị).
    - **Ghi nhận lượt xem / tải:** Mỗi lần tải file PDF chính thức cần ghi `AuditLog` (ai tải, lúc nào, trên văn bản nào, từ IP nào) để phục vụ kiểm soát phát tán văn bản mật.
    - **Chỉ cho phép tải file chính thức (đã ký số):** Người dùng chỉ được tải bản PDF đã đóng dấu chứng nhận, không được phép truy cập file nháp của các phiên bản trung gian.

---

### 1.3. UC17: Theo dõi trạng thái văn bản
- **Tác nhân:** Cán bộ văn thư, Người soạn thảo, Người duyệt / Ký nháy.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[extend] Tra cứu văn bản của tôi` (Văn bản tôi tạo / Chờ tôi duyệt / Tôi đã duyệt xong).
    - `[extend] Xem trạng thái hiện tại` (Dự thảo, Chờ ký nháy, Chờ phê duyệt, Đã ban hành, Bị từ chối).
    - `[extend] Xem lịch sử chuyển trạng thái (Timeline / Workflow Audit Trail)`.
    - `[extend] Xem tiến trình xử lý` (Ai đang giữ, đã chờ bao nhiêu ngày).
    - `[extend] Lọc theo trạng thái và thời gian`.
  - 🎯 **Theo core của thầy (Lưu vết theo các cấp):**
    - **Timeline điều hành đầy đủ:** Từng bước trong vòng đời văn bản phải được ghi lại với: Người thực hiện, Vai trò/Chức vụ, Thời gian, Hành động, Ý kiến/Bút phê lãnh đạo (nếu có). Đây là bằng chứng lưu vết điều hành theo đúng yêu cầu đề tài.
    - **Hiển thị Ý kiến chỉ đạo / Bút phê lãnh đạo trong Timeline:** Khi xem tiến trình, người dùng thấy được ý kiến chỉ đạo cụ thể mà lãnh đạo đã ghi khi duyệt văn bản.
    - **Cảnh báo văn bản tồn đọng quá lâu:** Hệ thống tự động đánh dấu và gửi thông báo khi văn bản chờ duyệt quá X ngày mà chưa được xử lý (cấu hình theo SLA của từng loại văn bản).

---

## 2. Hướng Dẫn Kỹ Thuật Hoàn Thiện Nhóm Chức Năng

### Bước 1: Entity `DocumentDistribution` (Phạm vi phát hành)
```java
@Entity
@Table(name = "document_distributions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DocumentDistribution extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType; // ALL (Toàn đơn vị), ORG_NODE (Đơn vị/Khoa), USER (Cá nhân)

    @Column(name = "target_id")
    private Long targetId; // org_node_id hoặc user_id (null nếu ALL)

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private Instant readAt;
}
```

### Bước 2: Ký phát hành và phân phối qua workflow

Sử dụng `POST /documents/{id}/workflow/sign-and-publish`, được xử lý bởi
`DocumentWorkflowServiceImpl.signAndPublish()`. Request là `DocumentPublishRequest`:

```json
{
  "distributionScope": "ALL",
  "recipientOrgIds": []
}
```

- `ALL`: toàn trường, không kèm đơn vị nhận.
- `SINGLE_ORG`: đúng một ID đơn vị nhận khác nhau.
- `MULTIPLE_ORGS`: ít nhất hai ID đơn vị nhận khác nhau.

Văn thư cấp số và ký nháy bằng các thao tác riêng trước khi phát hành. Service phát hành kiểm tra quyền, nơi nhận và các chữ ký trên PDF hiện tại, ký niêm phong rồi lưu bản
chính thức, hash và chuyển trạng thái từ `APPROVED` sang `PUBLISHED`, service lưu
các bản ghi phân phối `ALL` hoặc `ORG` trong cùng transaction. API phát hành trực
tiếp cũ đã được loại bỏ; không dùng nó để bỏ qua workflow.

### Bước 3: API Verify Tính Toàn Vẹn (`GET /api/v1/documents/{id}/verify-integrity`)
```java
@GetMapping("/{id}/verify-integrity")
public BaseResponse<IntegrityVerifyResponse> verifyIntegrity(@PathVariable Long id) {
    Document doc = documentRepository.findById(id).orElseThrow();
    String storedHash = doc.getOfficialHash();

    // Tính lại hash của file đang lưu trong Storage
    String computedHash = fileStorageService.computeFileSha256(doc.getOfficialFilePath());

    boolean isValid = storedHash.equals(computedHash);
    return BaseResponse.success(IntegrityVerifyResponse.builder()
        .valid(isValid)
        .documentId(id)
        .officialNumber(doc.getOfficialNumber())
        .hashStored(storedHash)
        .hashComputed(computedHash)
        .verifiedAt(Instant.now())
        .build());
}
```

### Bước 4: API Timeline Theo Dõi Tiến Trình
- `GET /api/v1/documents/my-documents`: Danh sách văn bản do user hiện tại tạo.
- `GET /api/v1/documents/inbox`: Danh sách văn bản gửi tới phòng ban của user.
- `GET /api/v1/documents/{id}/timeline`: Timeline chi tiết từng bước xử lý kèm bút phê lãnh đạo.
- `GET /api/v1/documents/pending-review`: Danh sách văn bản chờ user hiện tại xử lý (kèm số ngày tồn đọng).

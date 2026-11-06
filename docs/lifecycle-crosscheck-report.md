# Báo cáo đối chiếu — Luồng văn bản đến ban hành

> **Rà soát ngày:** 2026-11-05  
> **Mốc tài liệu gốc:** `document-lifecycle-remediation.md`  
> **Mốc code:** `92ade22` (đọc lại tại thời điểm rà soát)  
> **Phân loại:** ✅ Khớp · ⚠️ Khớp một phần / cần lưu ý · ❌ Sai / mâu thuẫn với code

---

## 1. Luồng và mốc đã có

### 1.1 Luồng chính 5 bước

✅ **Khớp.** `WorkflowEngine.TRANSITION_MATRIX` xác nhận đúng thứ tự:

| Action | Từ | Đến |
|---|---|---|
| `SUBMIT` | `DRAFT` | `IN_REVIEW` |
| `REVIEW_APPROVE` | `IN_REVIEW` | `PENDING_APPROVAL` |
| `LEADER_APPROVE` | `PENDING_APPROVAL` | `APPROVED` |
| `SIGN_AND_PUBLISH` | `APPROVED` | `PUBLISHED` |
| `REVOKE` | `PUBLISHED` | `REVOKED` |

### 1.2 Hai tuyến gửi đặc biệt

✅ **Khớp.** `TRANSITION_MATRIX` có đủ cả ba tuyến từ `DRAFT`:

| Action | Đến | Điều kiện |
|---|---|---|
| `SUBMIT` | `IN_REVIEW` | Tuyến chuẩn qua thẩm định |
| `SUBMIT_TO_LEADER` | `PENDING_APPROVAL` | Tuyến thẳng lên lãnh đạo |
| `SUBMIT_TO_CLERK_QUEUE` | `APPROVED` | Tuyến thẳng vào hàng chờ văn thư |

Logic routing nằm trong `SubmissionRoutingService.resolve()` được gọi từ `DocumentApprovalService.submitForApproval()`.

### 1.3 `PENDING_SIGN` có trong enum nhưng chưa nối vào engine

✅ **Khớp chính xác.**

- `DocumentStatus.java` định nghĩa `PENDING_SIGN("Chờ ký số")`.
- `WorkflowEngine.TRANSITION_MATRIX` **không có entry** cho `PENDING_SIGN` → không có transition nào dẫn vào hoặc ra trạng thái này.
- `DocumentWorkflowServiceImpl` dùng `PENDING_SIGN` chỉ ở endpoint danh sách (`getPendingSignDocuments`) nhưng không có action nào đặt văn bản vào trạng thái này.

### 1.4 `ISSUED` — tài liệu gốc không nhắc đến

⚠️ **Tài liệu không nhắc** `ISSUED`. Tuy nhiên enum định nghĩa `ISSUED("Đã phát hành")` và `TRANSITION_MATRIX` cũng **không có entry** cho trạng thái này — tình trạng giống `PENDING_SIGN`.

### 1.5 `GET /documents/review-inbox`

✅ **Khớp.** Endpoint tồn tại trong `DocumentWorkflowController`, delegate sang `DocumentWorkflowServiceImpl.getReviewInbox()`. Hộp thư lọc theo `findReviewInbox(viewerId, page)` từ `ReviewAssignmentPolicy`, phân trang nhưng không sort theo `submittedAt DESC` (cần kiểm tra query thực tế trong repository).

---

## 2. Đối chiếu từng điểm việc

### Nhóm A — Dự thảo, tệp chính và gửi duyệt

#### A2 — Chống gửi trùng

⚠️ **Tên method trong tài liệu gốc ghi sai.**  
Tài liệu nhắc `submitForApproval` — method **thực sự tồn tại** trong `DocumentApprovalService.submitForApproval(Long documentId, SubmitApprovalRequest request, Long userId)`. Tuy nhiên không có lock ở tầng service cho concurrent submit (chỉ có `@Transactional`, không có pessimistic lock trên `Document`).

#### A3 — Thu hồi và gửi lại

✅ **Khớp.** `TRANSITION_MATRIX`:
- `IN_REVIEW + WITHDRAW → DRAFT`
- `NEEDS_REVISION + SUBMIT → IN_REVIEW`
- `NEEDS_REVISION + WITHDRAW → DRAFT`

`recallApproval()` trong `DocumentApprovalService` kiểm tra `latestRequest.getStatus() != PENDING` trước khi cho thu hồi.

#### A4 — Đóng `REJECTED` (F03) ❌

❌ **Tài liệu nêu đúng lỗi. Xác nhận từ code.**

`TRANSITION_MATRIX` vẫn cho:
```
REJECTED + SUBMIT  → IN_REVIEW
REJECTED + WITHDRAW → DRAFT
```

`submitForApproval()` trong `DocumentApprovalService` cũng cho phép status `REJECTED`:
```java
if (document.getStatus() != DocumentStatus.DRAFT
    && document.getStatus() != DocumentStatus.REJECTED
    && document.getStatus() != DocumentStatus.NEEDS_REVISION) {
    throw ...
}
```

Hồ sơ bị từ chối **vẫn có thể gửi lại với cùng ID** — vi phạm quy tắc đóng hồ sơ được đề xuất.

---

### Nhóm B — Thẩm định và phê duyệt lãnh đạo

#### B1 — Bắt buộc PDF và chữ ký ở bước duyệt ⚠️

⚠️ **Tài liệu nêu đúng vấn đề, nhưng code đã có `ensureDocumentPdfExists` ở một số nơi.**

Trong `DocumentWorkflowServiceImpl.approve()`:
```java
// Nhánh REVIEW_APPROVE:
byte[] currentPdfBytes = loadDocumentPdfBytesSafely(document);
if (currentPdfBytes != null && currentPdfBytes.length > 0 && pdfSigner != null) {
    // ký...
}
// → Nếu null: approve tiếp tục KHÔNG có chữ ký
```

Ngược lại, `initialSign()` và `signAndPublish()` đều gọi `ensureDocumentPdfExists()` **trước** nên sẽ throw khi thiếu PDF.  
`approve()` **thiếu guard tương đương** — nếu PDF bị null vẫn chuyển trạng thái mà không ký.

#### B2 — Ký lặp giữa `initial-sign` và `approve` ❌

❌ **Tài liệu nêu đúng. Xác nhận từ code.**

`approve()` ở nhánh `IN_REVIEW` tự gọi `pdfSigner.signBytes()` (ký nháy lần 2 bằng actor hiện hành):
```java
if (action == WorkflowAction.REVIEW_APPROVE) {
    byte[] currentPdfBytes = loadDocumentPdfBytesSafely(document);
    if (currentPdfBytes != null && ...) {
        signResult = pdfSigner.signBytes(currentPdfBytes, ...);
        ...
    }
}
```

Nếu user đã gọi `initial-sign` trước → `approve` sẽ cố ký lại cùng user trên cùng PDF. `PdfSigner` phát hiện và ném `IllegalStateException("User đã ký")`, sau đó được wrap thành `BusinessException` nhưng **action vẫn tiếp tục không ký** (không throw ra ngoài rõ ràng trong một số path).

#### B3 — Hoàn tất request và bàn giao

⚠️ **Chưa đối chiếu đầy đủ.** Cần đọc thêm logic update `DocumentApprovalRequest.status` khi approve/reject để xác nhận yêu cầu được đóng đúng vòng.

#### B4 — Guard cấp lãnh đạo và chống tự duyệt ⚠️

⚠️ **Tài liệu nêu đúng: guard chỉ dựa vào role, không kiểm tra đích danh.**

`DocumentWorkflowController`:
```java
@PreAuthorize("hasAnyRole('LEADER', 'REVIEWER', 'ADMIN', 'SYSTEM_ADMIN')")
@PostMapping("/{id}/workflow/approve")
```

`WorkflowEngine.validateActorPermission()` gọi `ReviewAssignmentPolicy.applies()` rồi `requireAssignedActor()` — đây là điểm kiểm tra đích danh ở `IN_REVIEW`. Nhưng ở `PENDING_APPROVAL` policy này chưa chắc có entry tương đương (cần xác minh `ReviewAssignmentPolicy` có rule cho PENDING_APPROVAL không).

---

### Nhóm C — Văn thư, cấp số và ban hành

#### C2 — Hai API phát hành song song (F08) ❌

❌ **Tài liệu nêu đúng hai vấn đề. Cả hai xác nhận từ code.**

**Vấn đề 1:** `DocumentPublishService.publishDocument()` chấp nhận `DRAFT`:
```java
Set<DocumentStatus> validStatuses = Set.of(
    DocumentStatus.APPROVED,
    DocumentStatus.DRAFT   // ← đường tắt bỏ qua toàn bộ workflow
);
```

**Vấn đề 2:** `DocumentWorkflowController.signAndPublish()` thiếu `CLERK` trong guard:
```java
@PreAuthorize("hasAnyRole('LEADER', 'ADMIN', 'SYSTEM_ADMIN')")
// ← CLERK không có quyền gọi sign-and-publish
```

#### C3 — Chốt thời điểm ký, niêm phong và commit

✅ **Khớp.** `signAndPublish()` trong `DocumentWorkflowServiceImpl`:
1. Cấp số từ sổ (với lock) hoặc kế thừa số đã cấp
2. Ký nháy văn thư (`pdfSigner.signBytes`) → `pdfToCertify`
3. Ký certify + niêm phong DocMDP (`pdfSigner.signAndCertifyBytes`) → `certifiedPdfBytes`
4. Lưu storage, tính SHA-256, gán `officialFilePath` và `officialHash`
5. `workflowEngine.executeTransition(SIGN_AND_PUBLISH)` → `PUBLISHED`

Thứ tự đúng, nhưng **tệp đã ghi vào storage trước khi transaction DB commit** — rollback DB không tự xóa tệp (tài liệu đã ghi nhận rủi ro này ở mục C3).

#### C4 — Phân phối và quyền đọc ❌

❌ **Tài liệu nêu đúng. Xác nhận từ code.**

`DocumentPublishService.extractTargetIds()` dùng reflection:
```java
// Thử getTargetIds() → không tìm thấy trong PublishRequest
// Fallback getOrgIds()  → không tìm thấy trong PublishRequest
// → trả Collections.emptyList()
```

`PublishRequest.java` khai báo field là `recipientOrgIds` với getter `getRecipientOrgIds()`. Cả `getTargetIds` lẫn `getOrgIds` **đều không tồn tại** trong class này → `extractTargetIds()` luôn trả danh sách rỗng → văn bản phát hành không được phân phối cho bất kỳ đơn vị nào.

---

## 3. Tổng hợp và tiến độ khắc phục

| ID | Điểm | Trạng thái đối chiếu | Mức độ | Tiến độ xử lý (2026-11-05) |
|---|---|---|---|---|
| — | Luồng chính 5 bước | ✅ Khớp | — | — |
| — | Hai tuyến gửi đặc biệt | ✅ Khớp | — | — |
| — | `PENDING_SIGN` chưa nối | ✅ Khớp | — | — |
| A2 | Tên method `submitForApproval` | ✅ Tồn tại (tên đúng) | — | — |
| A2 | Chống gửi trùng (concurrent lock) | ⚠️ Chưa có lock | Trung bình | ⏳ Chờ xử lý |
| A3 | Thu hồi và gửi lại | ✅ Khớp | — | — |
| **A4** | REJECTED cho tái gửi cùng ID | ❌ Lỗi xác nhận | **Cao** | ⏳ Chờ xử lý |
| **B1** | `approve()` bỏ qua ký khi thiếu PDF | ❌ Lỗi xác nhận | **Cao** | ✅ **ĐÃ SỬA** (bắt buộc PDF + ký số) |
| **B2** | Ký lặp `initial-sign` → `approve` | ❌ Lỗi xác nhận | **Cao** | ⏳ Chờ chốt phương án |
| B3 | Cập nhật request khi duyệt | ⚠️ Cần đọc thêm | Trung bình | ⏳ Chờ xử lý |
| B4 | Guard đích danh PENDING_APPROVAL | ⚠️ Chưa xác minh đầy đủ | Trung bình | ⏳ Chờ xử lý |
| **C2** | `publishDocument` chấp nhận DRAFT | ❌ Lỗi xác nhận | **Cao** | ✅ **ĐÃ SỬA** (chỉ cho phép APPROVED) |
| C2 | `sign-and-publish` thiếu CLERK | ⚠️ Cần chốt nghiệp vụ | Trung bình | ⏳ Chờ chốt phân quyền văn thư |
| **C4** | Reflection sai field → phân phối rỗng | ❌ Lỗi xác nhận | **Cao** | ✅ **ĐÃ SỬA** (dùng `recipientOrgIds`) |

> Chi tiết các bước và mã nguồn sửa đổi xem tại [lifecycle-fix-guide.md](lifecycle-fix-guide.md).

---

*Tài liệu này sinh tự động từ rà soát code — cập nhật lại sau mỗi lần sửa các điểm trên.*


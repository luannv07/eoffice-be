# Hướng dẫn xử lý các vấn đề vòng đời văn bản (Document Lifecycle Remediation)

> **Mã nguồn:** `eoffice-be`  
> **Cập nhật:** 2026-11-05  
> **Tham chiếu đối chiếu:** [lifecycle-crosscheck-report.md](lifecycle-crosscheck-report.md) · [document-lifecycle-remediation.md](document-lifecycle-remediation.md)

---

## Bảng theo dõi tiến độ xử lý

| Mã | Hạng mục | Trạng thái | Ngày xử lý | File liên quan |
|---|---|---|---|---|
| **C4** | Sửa `extractTargetIds` đọc sai trường reflection | ✅ **Đã hoàn thành** | 2026-11-05 | `DocumentPublishService.java` |
| **C2** | Chặn ban hành trực tiếp từ trạng thái `DRAFT` | ✅ **Đã hoàn thành** | 2026-11-05 | `DocumentPublishService.java` |
| **B1** | Bắt buộc PDF và chữ ký số ở bước duyệt `approve()` | ✅ **Đã hoàn thành** | 2026-11-05 | `DocumentWorkflowServiceImpl.java` |
| **A4** | Đóng hồ sơ `REJECTED`, chặn gửi lại/sửa cùng ID | ⏳ Chưa xử lý | — | `WorkflowEngine.java`, `DocumentApprovalService.java` |
| **B2** | Xử lý ký lặp giữa `initial-sign` và `approve` | ⏳ Chờ chốt phương án | — | `DocumentWorkflowServiceImpl.java` |
| **B4** | Guard đích danh người nhận ở `PENDING_APPROVAL` | ⏳ Chờ chốt phân nhiệm | — | `ReviewAssignmentPolicy.java`, `WorkflowEngine.java` |
| **C2b**| Thêm quyền `CLERK` vào `/workflow/sign-and-publish` | ⏳ Chờ chốt luồng văn thư | — | `DocumentWorkflowController.java` |
| **A2** | Chống gửi trùng đồng thời (`Pessimistic Lock`) | ⏳ Đang chờ | — | `DocumentApprovalService.java` |
| **C3** | Dọn dẹp tệp PDF orphan khi rollback transaction | ⏳ Đang chờ | — | `DocumentWorkflowServiceImpl.java` |

---

## Chi tiết các lỗi ĐÃ SỬA (2026-11-05)

### 1. Fix C4 — Sửa phân phối văn bản rỗng do reflection sai trường
- **File:** `src/main/java/vn/edu/epu/eoffice_be/service/DocumentPublishService.java`
- **Hiện tượng cũ:** Khi ban hành văn bản, `extractTargetIds` cố gắng gọi `getTargetIds()` rồi fallback sang `getOrgIds()` bằng reflection. Trong khi đó `PublishRequest.java` định nghĩa trường là `recipientOrgIds` (getter: `getRecipientOrgIds()`). Do đó hàm luôn trả về danh sách rỗng, bảng `document_distribution` không được tạo bản ghi nào, khiến các đơn vị nhận không thể tra cứu văn bản.
- **Giải pháp đã thực hiện:**
  Xóa bỏ cơ chế reflection dễ gãy, gọi trực tiếp getter của DTO:
  ```java
  private List<Long> extractTargetIds(PublishRequest request) {
      if (request == null || request.getRecipientOrgIds() == null) {
          return Collections.emptyList();
      }
      return request.getRecipientOrgIds();
  }
  ```

---

### 2. Fix C2 — Chặn ban hành văn bản từ trạng thái `DRAFT` (Đường tắt bypass workflow)
- **File:** `src/main/java/vn/edu/epu/eoffice_be/service/DocumentPublishService.java`
- **Hiện tượng cũ:** Phương thức `publishDocument` cho phép cả `DocumentStatus.APPROVED` và `DocumentStatus.DRAFT` (`Set.of(APPROVED, DRAFT)`). Điều này cho phép người dùng hoặc client gọi API `/documents/{id}/publish` để phát hành ngay một văn bản nháp mà không cần thông qua các bước thẩm định, phê duyệt và ký số bắt buộc.
- **Giải pháp đã thực hiện:**
  Loại bỏ hoàn toàn `DocumentStatus.DRAFT`, chỉ cho phép trạng thái `APPROVED`:
  ```java
  // Fix C2: chỉ cho phép APPROVED — loại bỏ đường tắt từ DRAFT bỏ qua workflow.
  if (doc.getStatus() != DocumentStatus.APPROVED) {
      throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATUS, 
              "Chỉ có thể ban hành văn bản đã được phê duyệt (trạng thái APPROVED). "
              + "Trạng thái hiện tại: " + (doc.getStatus() != null ? doc.getStatus().name() : "null"));
  }
  ```

---

### 3. Fix B1 — Bắt buộc PDF và ký số khi phê duyệt (`approve`)
- **File:** `src/main/java/vn/edu/epu/eoffice_be/service/impl/DocumentWorkflowServiceImpl.java`
- **Hiện tượng cũ:**
  - `approve()` sử dụng helper `loadDocumentPdfBytesSafely(document)` bắt nuốt lỗi `DOCUMENT_PRIMARY_FILE_MISSING` và trả về `null`.
  - Khối ký số được bọc trong điều kiện lỏng lẻo `if (currentPdfBytes != null && currentPdfBytes.length > 0 && pdfSigner != null)`.
  - Nếu văn bản không có PDF hoặc storage gặp sự cố, hệ thống vẫn âm thầm thực hiện chuyển trạng thái workflow (`executeTransition`) mà không hề tạo chữ ký số hay ghi nhận audit hash.
- **Giải pháp đã thực hiện:**
  1. Gọi `ensureDocumentPdfExists(document)` ngay đầu hàm `approve()` (ngay sau bước kiểm tra quyền) để fail-fast nếu văn bản thiếu file PDF.
  2. Kiểm tra `pdfSigner` sẵn sàng, ném `ErrorCode.SIGNATURE_PROCESS_ERROR` nếu chưa khả dụng.
  3. Sử dụng trực tiếp `loadDocumentPdfBytes(document)` thay vì helper nuốt lỗi.
  4. Đảm bảo `signatureHash` luôn được tạo ra từ kết quả ký (`signBytes` hoặc `signVisibleBytes`) và truyền vào `executeTransition`.
  5. Xóa bỏ hoàn toàn phương thức không an toàn `loadDocumentPdfBytesSafely`.

---

## Kế hoạch và hướng dẫn xử lý các vấn đề CÒN LẠI

### 4. A4 — Đóng hồ sơ `REJECTED` (F03)
- **Vấn đề:** `WorkflowEngine.TRANSITION_MATRIX` vẫn cho phép `REJECTED + SUBMIT -> IN_REVIEW` và `REJECTED + WITHDRAW -> DRAFT`. `DocumentApprovalService.submitForApproval` vẫn chấp nhận trạng thái `REJECTED`.
- **Hướng giải quyết:**
  - Xóa transition `SUBMIT` và `WITHDRAW` khỏi nhóm `REJECTED` trong `TRANSITION_MATRIX`.
  - Xóa `document.getStatus() != DocumentStatus.REJECTED` khỏi điều kiện kiểm tra trong `submitForApproval`.
  - Thêm guard chặn chỉnh sửa, tải lên tệp đính kèm trên hồ sơ đã bị từ chối. Hồ sơ đề xuất lại bắt buộc tạo văn bản mới (ID mới).

### 5. B2 — Chống ký lặp giữa `initial-sign` và `approve`
- **Vấn đề:** Nếu cán bộ thẩm định đã ký nháy bằng `/workflow/initial-sign`, sau đó bấm duyệt `/workflow/approve`, nhánh `REVIEW_APPROVE` lại cố ký lại một lần nữa bằng cùng user identity trên cùng một file PDF, dẫn đến lỗi trùng chữ ký (`User đã ký...`).
- **Hướng giải quyết:**
  - *Phương án A (Tách biệt):* Ở `approve()`, kiểm tra xem actor đã có chữ ký hợp lệ trên PDF chưa (`pdfSignatureVerifier.hasSignatureByUser`). Nếu đã có chữ ký, chuyển tiếp trạng thái mà không cần ký đè.
  - *Phương án B (Gộp luồng):* Hợp nhất ký nháy vào bước duyệt duy nhất.

### 6. B4 — Guard kiểm tra đích danh người nhận ở `PENDING_APPROVAL`
- **Vấn đề:** `ReviewAssignmentPolicy` hiện chỉ ràng buộc người được giao ở trạng thái `IN_REVIEW`. Ở `PENDING_APPROVAL`, bất kỳ ai có role `LEADER` đều có thể gọi `approve`.
- **Hướng giải quyết:**
  - Bổ sung quy tắc trong `ReviewAssignmentPolicy` để kiểm tra lãnh đạo được phân công duyệt cụ thể (được chỉ định từ bước thẩm định chuyển lên hoặc tuyến gửi trực tiếp).

### 7. C2b — Guard quyền văn thư (`CLERK`) cho `sign-and-publish`
- **Vấn đề:** `DocumentWorkflowController.signAndPublish()` hiện có role gate:
  `@PreAuthorize("hasAnyRole('LEADER', 'ADMIN', 'SYSTEM_ADMIN')")`
  Thiếu `CLERK`, trong khi nghiệp vụ văn thư là người thực hiện cấp số và phát hành.
- **Hướng giải quyết:**
  - Cập nhật `@PreAuthorize("hasAnyRole('LEADER', 'CLERK', 'ADMIN', 'SYSTEM_ADMIN')")`.
  - Đảm bảo vai trò `CLERK` được gán quyền `DOCUMENT_SIGN_OFFICIAL` trong `RolePermissionCatalog`.


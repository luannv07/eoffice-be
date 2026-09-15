# 📐 Hướng Dẫn Sửa Sơ Đồ UC & Biểu Đồ Hoạt Động Theo Core Đề Tài

> Tài liệu này hướng dẫn cụ thể **nên thêm/sửa gì** vào từng sơ đồ Use Case và Biểu đồ Hoạt động đã vẽ để chuẩn hóa theo 4 điểm core của đề tài DevSecOps.

---

## 🔑 4 Điểm Core Cần Phản Ánh Vào Sơ Đồ

| # | Điểm core | Ảnh hưởng đến UC |
|:---:|:---|:---|
| 1 | **Chữ ký số & Chống giả mạo** (PKI / SHA-256 / Tamper-proof) | UC13, UC14, UC15 |
| 2 | **Sổ văn bản 2 cấp** (Cấp Đơn vị & Cấp Phòng/Ban, reset năm mới) | UC06, UC07, UC15 |
| 3 | **Cây tổ chức động** (Re-parent tự động dịch chuyển luồng điều hành) | UC03, UC04, UC11 |
| 4 | **Bút phê lãnh đạo & Văn bản liên kết** (Endorsement + Parent/Child Doc) | UC08, UC12, UC14, UC18 |

---

## 📌 ĐIỂM CORE 1: Chữ Ký Số & Chống Giả Mạo

### UC13 — Ký nháy văn bản
**Trong Sơ đồ UC hiện tại:**
- Đang có: `[extend] Xác nhận ký nháy`
- **Cần đổi tên** `Xác nhận ký nháy` → `Ký nháy điện tử (Digital Stamp)`
- **Thêm mới** `[extend] Hệ thống nhúng dấu ký nháy vào PDF` *(System actor)*

**Trong Biểu đồ Hoạt động UC13 — Thêm các bước sau:**
```
[Người duyệt bấm "Ký nháy"]
        ↓
[Hệ thống lấy file PDF từ Storage]
        ↓
[Hệ thống tạo Digital Stamp:
 Họ tên + Chức vụ + Thời gian ký + Ảnh chữ ký mẫu]
        ↓
[Hệ thống nhúng Stamp vào trang PDF quy định]
        ↓
[Hệ thống tính SHA-256 hash của PDF sau ký]
        ↓
[Hệ thống lưu PDF mới + signature_hash vào WorkflowLog]
        ↓
[Gửi thông báo tới người xử lý tiếp theo]
```

---

### UC14 — Phê duyệt và ký phát hành
**Trong Sơ đồ UC hiện tại:**
- Đang có: `[extend] Ký phát hành văn bản`
- **Đổi tên** → `Ký số phát hành (PKI / X.509)`
- **Thêm mới** `[extend] Hệ thống đóng dấu chứng nhận chống sửa đổi` *(System)*
- **Thêm mới** `[extend] Hệ thống lưu SHA-256 (official_hash)` *(System)*

**Trong Biểu đồ Hoạt động UC14 — Thêm luồng sau sau bước "Phê duyệt":**
```
[Người ký bấm "Ký phát hành"]
        ↓
[Hệ thống tổng hợp bản PDF cuối:
 Nội dung + Số hiệu chính thức + Ngày ban hành]
        ↓
[Hệ thống ký số X.509 bằng Private Key của người ký]
        ↓
[Hệ thống đặt cờ: CERTIFIED_NO_CHANGES_ALLOWED]
        ↓
[Hệ thống tính SHA-256 → lưu vào documents.official_hash]
        ↓
[Hệ thống lưu PDF chính thức vào thư mục Storage riêng]
        ↓
[Chuyển sang UC15 - Phát hành]
```

---

### UC15 — Phát hành văn bản *(Thêm bước verify)*
**Trong Biểu đồ Hoạt động UC15 — Thêm bước sau bước "Lưu hồ sơ phát hành":**
```
[Hệ thống ghi nhận official_hash vào DB]
        ↓
[Hệ thống tạo phạm vi phát hành (DocumentDistribution)]
        ↓
[Hệ thống gửi thông báo tới đơn vị/người nhận]
```

**Thêm UC mới hoặc include vào UC15/UC18:**
> `[extend] Xác minh tính toàn vẹn văn bản (Verify Integrity)`
> - Actor: Người dùng
> - Hệ thống tính lại SHA-256 → So sánh với `official_hash` → Trả kết quả `Hợp lệ / Đã bị can thiệp`

---

## 📌 ĐIỂM CORE 2: Sổ Văn Bản 2 Cấp

### UC06 — Quản lý sổ văn bản
**Trong Sơ đồ UC hiện tại — Thêm các chức năng:**
- `[include] Xem danh sách sổ theo cấp` *(cấp 1: Đơn vị / cấp 2: Phòng Ban)*
- `[extend] Lọc sổ theo cấp (Cấp 1 / Cấp 2)`
- `[extend] Đóng sổ cuối năm`

**Khi vẽ Biểu đồ Hoạt động UC06 (Tạo mới sổ):**
```
[Cán bộ văn thư chọn "Tạo mới sổ"]
        ↓
[Chọn Loại văn bản] ──── (Mỗi loại có sổ riêng)
        ↓
[Chọn Cấp sổ]
   ├─ Cấp 1: Sổ của Đơn vị/Trường  → Số hiệu: {STT}/QĐ-ĐHEPU
   └─ Cấp 2: Sổ của Phòng/Ban      → Số hiệu: {STT}/TB-CNTT
        ↓
[Chọn Đơn vị sở hữu sổ (OrgNode)]
        ↓
[Nhập tên sổ, năm, pattern sinh số]
        ↓
[Hệ thống lưu sổ với current_number = 0]
```

---

### UC07 — Cấp số văn bản
**Trong Biểu đồ Hoạt động UC07 — Làm rõ luồng thread-safe:**
```
[Cán bộ văn thư chọn văn bản để cấp số]
        ↓
[Kiểm tra trạng thái văn bản = APPROVED?]
   └─ Không → [Thông báo "Chưa đủ điều kiện cấp số"] → Kết thúc
        ↓ Có
[Chọn sổ văn bản phù hợp (theo loại VB + cấp + đơn vị)]
        ↓
[Hệ thống khóa sổ (Pessimistic Lock) để tránh trùng số]
        ↓
[Tăng current_number + 1]
        ↓
[Sinh số hiệu theo pattern: {STT}/{MÃ_LOẠI}-{MÃ_ĐƠN_VỊ}]
        ↓
[Ghi số hiệu + ngày ban hành vào Document]
        ↓
[Hệ thống mở khóa sổ]
        ↓
[Chuyển sang UC15 - Phát hành]
```

**Thêm vào Biểu đồ Hoạt động — Nhánh "Hủy cấp số":**
```
[extend] Hủy cấp số:
[Cán bộ văn thư chọn "Hủy cấp số"]
        ↓
[Hệ thống xóa số hiệu trên Document]
        ↓
[Hệ thống giảm current_number - 1 trên sổ]  ← Chỉ được hủy số VỪA CẤP (số lớn nhất)
        ↓
[Ghi AuditLog: Hủy cấp số]
```

---

## 📌 ĐIỂM CORE 3: Cây Tổ Chức Động & Luồng Tự Động

### UC03 — Quản lý đơn vị trực thuộc
**Trong Sơ đồ UC hiện tại — Thêm:**
- Đổi tên `[extend] Chỉnh sửa thông tin đơn vị` → giữ nguyên, **thêm mới riêng:**
- `[extend] Di chuyển đơn vị sang nhánh cha mới (Re-parent)`

**Trong Biểu đồ Hoạt động — Thêm luồng "Di chuyển nhánh":**
```
[Admin kéo thả / chọn "Đổi đơn vị cha"]
        ↓
[Chọn đơn vị cha mới]
        ↓
[Hệ thống kiểm tra: newParent có phải là con của node hiện tại không?]
   └─ Có → [Lỗi: "Không thể tạo vòng lặp cây"] → Kết thúc
        ↓ Không
[Hệ thống XÓA toàn bộ bản ghi ancestor cũ trong org_node_closures
 của node và toàn bộ nhánh con]
        ↓
[Hệ thống TẠO LẠI bản ghi ancestor mới theo newParentId
 cho node và toàn bộ nhánh con]
        ↓
[Hệ thống cập nhật parent_id]
        ↓
⚡ [TỰ ĐỘNG: Toàn bộ quyền hạn nhân sự thuộc nhánh
    di chuyển được cập nhật ngay lập tức
    vì PermissionResolver query real-time từ org_node_closures]
        ↓
[Trả về cây tổ chức đã cập nhật]
```
> **Ghi chú để thêm vào sơ đồ:** Ghi chú `<<Automatically propagated>>` trên mũi tên liên kết UC03 với UC04 và UC11 để thể hiện rằng thay đổi cây tự động ảnh hưởng tới luồng phê duyệt.

---

### UC11 — Gửi văn bản để duyệt
**Trong Biểu đồ Hoạt động — Làm rõ bước "Xác định người duyệt":**
```
[Hệ thống xác định người duyệt tiếp theo]
        ↓
[Query PositionAssignment theo org_node_id của đơn vị ban hành]
        ↓
[Kết hợp OrgNodeClosure để lấy người duyệt cấp trên]
        ↓
⚡ [Vì dùng real-time query: nếu cơ cấu tổ chức vừa thay đổi (UC03),
    người duyệt được xác định đúng theo cơ cấu MỚI tự động]
```

---

## 📌 ĐIỂM CORE 4: Bút Phê Lãnh Đạo & Văn Bản Liên Kết

### UC08 — Tạo dự thảo văn bản
**Trong Sơ đồ UC — Thêm:**
- `[extend] Chọn văn bản căn cứ (Văn bản liên kết)`

**Trong Biểu đồ Hoạt động — Thêm bước tùy chọn:**
```
[Người soạn thảo nhập thông tin văn bản]
        ↓
[Tùy chọn: Văn bản này có căn cứ theo văn bản nào không?]
   ├─ Có → [Tìm kiếm & chọn văn bản cha (parent_document_id)]
   │         → [Hiển thị: "Căn cứ theo: QĐ-125/2026/QĐ-ĐHEPU"]
   └─ Không → Tiếp tục
        ↓
[Tiếp tục lưu dự thảo]
```

---

### UC12 — Duyệt hoặc yêu cầu chỉnh sửa
**Trong Sơ đồ UC — Thêm:**
- `[extend] Ghi ý kiến chỉ đạo (Bút phê lãnh đạo)`
- `[extend] Giao việc cho đơn vị chủ trì`

**Trong Biểu đồ Hoạt động — Sau bước "Ghi nhận xét", thêm nhánh:**
```
[Người duyệt xem nội dung]
        ↓
[Ghi nhận xét / Ý kiến]
        ↓
[Có ý kiến chỉ đạo cần giao việc không?]
   ├─ Có → [Nhập nội dung bút phê lãnh đạo]
   │         [Chọn Đơn vị chủ trì thực hiện (OrgNode)]
   │         [Hệ thống lưu leadership_instruction + assigned_org_node_id]
   │         [Hệ thống gửi thông báo tới đơn vị được giao]  ← QUAN TRỌNG
   └─ Không → Tiếp tục
        ↓
[Chọn hành động: Chấp thuận / Trả về / Chuyển ký nháy]
```

---

### UC18 — Tìm kiếm, xem chi tiết và lịch sử văn bản
**Trong Sơ đồ UC — Thêm:**
- `[extend] Xem chuỗi văn bản liên kết (Document Lineage)`
- `[extend] Xác minh tính toàn vẹn file (Verify Integrity)`
- `[extend] Xem bút phê / Ý kiến chỉ đạo của lãnh đạo`

**Trong Biểu đồ Hoạt động "Xem chi tiết văn bản" — Thêm tab/mục:**
```
Trang chi tiết văn bản hiển thị:
├─ [Thông tin văn bản] (Số hiệu, Loại, Ngày ban hành, Ngày hiệu lực)
├─ [Nơi nhận và lưu]
├─ [Tài liệu đính kèm chính thức] + Nút [Xác minh toàn vẹn]
├─ [Chuỗi liên kết]:
│    QĐ-125 (Văn bản gốc/cha)
│    └─ KH-45 (Kế hoạch triển khai)
│         └─ BC-12 (Báo cáo kết quả)
└─ [Lịch sử xử lý (Timeline)]:
     - 01/09 10:00 | Nguyễn Văn A (Soạn thảo) | Tạo dự thảo
     - 02/09 09:15 | Trần Thị B (Trưởng phòng) | Ký nháy | "Đề nghị bổ sung mục 3"
     - 03/09 14:00 | Lê Văn C (Phó Hiệu trưởng) | Phê duyệt
                    💬 Bút phê: "Giao Phòng Đào tạo chủ trì, hoàn thành 30/09"
     - 03/09 15:30 | Phạm Thị D (Văn thư) | Phát hành | Số: 125/QĐ-ĐHEPU
```

---

## 📋 Checklist Nhanh — Kiểm Tra Trước Khi Nộp Sơ Đồ

### ✅ Sơ đồ Use Case tổng thể (Overall UC Diagram)
- [ ] UC13 có tác nhân System cho bước "Nhúng Digital Stamp vào PDF"?
- [ ] UC14 có tác nhân System cho bước "Ký số PKI" và "Lưu SHA-256"?
- [ ] UC15 có `[extend] Xác minh tính toàn vẹn`?
- [ ] UC06 phân biệt rõ Sổ Cấp 1 và Sổ Cấp 2?
- [ ] UC03 có `[extend] Di chuyển nhánh (Re-parent)` với ghi chú tự động cập nhật luồng?
- [ ] UC08 có `[extend] Chọn văn bản căn cứ`?
- [ ] UC12 có `[extend] Ghi ý kiến chỉ đạo / Bút phê lãnh đạo`?
- [ ] UC18 có `[extend] Xem chuỗi văn bản liên kết` và `[extend] Xem bút phê lãnh đạo`?

### ✅ Biểu đồ Hoạt động (Activity Diagram) theo từng UC
- [ ] UC07: Có bước "Hệ thống khóa sổ (Pessimistic Lock)" trước khi tăng số?
- [ ] UC13: Có 4 bước riêng biệt: Lấy PDF → Tạo Stamp → Nhúng vào PDF → Tính Hash → Lưu?
- [ ] UC14: Có bước "CERTIFIED_NO_CHANGES_ALLOWED" và "Lưu official_hash"?
- [ ] UC03 (Di chuyển nhánh): Có bước "Xóa closure cũ → Tạo closure mới → Tự động cập nhật quyền"?
- [ ] UC12: Có nhánh "Bút phê lãnh đạo → Giao việc cho đơn vị → Gửi thông báo tới đơn vị"?
- [ ] UC11: Có ghi chú "Người duyệt xác định real-time theo PositionAssignment + OrgNodeClosure"?

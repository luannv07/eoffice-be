# 🔍 Nhóm Chức Năng 5: Tra Cứu, Audit Log & Quản Lý Thông Báo (UC18 - UC20)

Tài liệu này chi tiết hóa hiện trạng, các thành phần còn thiếu và hướng dẫn kỹ thuật triển khai cho nhóm **Tra Cứu, Audit Log & Quản Lý Thông Báo**.

---

## 1. Chi Tiết Từng Use Case

### 1.1. UC18: Tìm kiếm, xem chi tiết và lịch sử văn bản
- **Tác nhân:** Người dùng hệ thống.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[extend] Tìm kiếm đa tiêu chí`:
      - Tra cứu theo số hiệu văn bản (`official_number`).
      - Tra cứu theo loại văn bản (`document_type_id`).
      - Tra cứu theo ngày ban hành / ngày hiệu lực (từ ngày - đến ngày).
      - Tra cứu theo đơn vị ban hành (`issuing_org_id`).
      - Tra cứu theo trạng thái và trích yếu nội dung (Full-text search).
    - `[extend] Xem chi tiết văn bản` (Metadata, tệp đính kèm, danh sách ký duyệt, bút phê lãnh đạo).
    - `[extend] Xem lịch sử chỉnh sửa và thay đổi của văn bản`.
  - 🎯 **Theo core của thầy:**
    - **Xem chuỗi văn bản liên kết (Document Lineage):** Khi xem chi tiết một văn bản, hiển thị chuỗi liên kết: Văn bản gốc (cha) → Các văn bản phái sinh (con căn cứ theo văn bản này). Ví dụ: `QĐ-125 → KH-45 (Kế hoạch triển khai) → BC-12 (Báo cáo kết quả)`.
    - **Xem Bút phê / Ý kiến chỉ đạo của lãnh đạo trong chi tiết văn bản:** Mỗi bước duyệt có nhận xét/ý kiến chỉ đạo đều hiển thị rõ trong phần lịch sử xử lý.
    - **Kiểm tra tính toàn vẹn file (Verify Integrity):** Nút "Xác minh tính toàn vẹn" tính lại hash SHA-256 của file chính thức và so sánh với `official_hash` lưu trong DB → Xác nhận file không bị giả mạo kể từ khi phát hành.

---

### 1.2. UC19: Quản lý Audit Log
- **Tác nhân:** Quản trị viên (ADMIN).
- **Hiện trạng:** 🟡 **Hoàn thành 40% (Đã có hạ tầng BaseEntity & AOP logging)**
  - ✅ **Đã có:**
    - JPA Auditing tự động lưu `created_by`, `created_date`, `last_modified_by`, `last_modified_date` vào tất cả các thực thể kế thừa `BaseEntity`.
    - Package `audit` chứa cơ chế AOP / Interceptor.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Entity & Table lưu vết chi tiết AuditLog` (`audit_logs`: `username`, `action`, `resource_name`, `resource_id`, `ip_address`, `user_agent`, `old_value`, `new_value`, `timestamp`).
    - `[include] Xem danh sách nhật ký hệ thống` (kèm phân trang, lọc theo thời gian, user, thao tác, module).
    - `[include] Xuất báo cáo Audit Log` ra file Excel / CSV (sử dụng Apache POI).
    - `[extend] Tìm kiếm và lọc nhật ký` (theo user, action, resource, khoảng thời gian).
    - `[extend] Xem chi tiết bản ghi nhật ký` (xem diff dữ liệu trước và sau khi sửa dạng JSON).
  - 🎯 **Theo core của thầy (DevSecOps - Kiểm soát toàn diện):**
    - **Audit Log phải ghi đầy đủ thông tin bảo mật:**
      - `ip_address`: IP của request (bắt buộc - phát hiện truy cập bất thường).
      - `user_agent`: Thông tin trình duyệt/ứng dụng.
      - `session_id` hoặc `jti`: JWT ID để truy vết phiên làm việc cụ thể.
      - `old_value` / `new_value`: JSON diff của dữ liệu trước/sau khi sửa đổi.
    - **Các sự kiện bắt buộc phải ghi Audit Log:**
      - Đăng nhập thành công / thất bại.
      - Thay đổi mật khẩu.
      - Tạo/Sửa/Khóa tài khoản.
      - Gán/Thu hồi vai trò.
      - Mọi thao tác trên văn bản quan trọng (Tạo, Gửi duyệt, Duyệt, Ký, Phát hành, Tải file, Verify Integrity).
    - **Bổ sung khía cạnh DevSecOps vào báo cáo đề tài:**
      - **SAST - SonarQube:** Đã có `sonar-project.properties` → cần chạy phân tích và fix hết các issue Critical/Blocker trước khi bảo vệ.
      - **OWASP Dependency-Check:** Tích hợp plugin quét CVE thư viện bên thứ ba vào Maven build.
      - **Mã hóa lưu trữ:** File văn bản mật mã hóa AES-256 trên Storage (MinIO SSE).

---

### 1.3. UC20: Quản lý thông báo
- **Tác nhân:** Người dùng hệ thống.
- **Hiện trạng:** 🔴 **Chưa triển khai**
  - ✅ **Đã hoàn thành:** Chưa có.
  - ❌ **Chức năng còn thiếu:**
    - `[include] Entity & Bảng thông báo In-app` (`notifications`).
    - `[include] Xem danh sách thông báo` của người dùng hiện tại (kèm số lượng chưa đọc).
    - `[include] Hệ thống tự động gửi email` khi: có văn bản cần duyệt, văn bản được duyệt/từ chối, văn bản mới phát hành.
    - `[extend] Đánh dấu thông báo đã đọc / Đánh dấu tất cả đã đọc`.
  - 🎯 **Theo core của thầy:**
    - **Thông báo là cầu nối điều hành theo văn bản:** Khi lãnh đạo ghi Ý kiến chỉ đạo/Bút phê (UC12), hệ thống phải tự động gửi thông báo tới đơn vị/phòng ban được giao chủ trì thực hiện (`assigned_org_node_id`) — không chỉ gửi cho người soạn thảo.
    - **Thông báo tồn đọng:** Gửi nhắc nhở tự động khi văn bản chờ duyệt quá thời hạn SLA (ví dụ: quá 3 ngày làm việc).

---

## 2. Hướng Dẫn Kỹ Thuật Hoàn Thiện Nhóm Chức Năng

### Bước 1: Xây dựng Module Tìm kiếm Văn bản Đa tiêu chí (UC18)
```java
public class DocumentSpecification {
    public static Specification<Document> filter(DocumentSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleteFlg"), false));
            predicates.add(cb.equal(root.get("status"), DocumentStatus.PUBLISHED));
            
            if (StringUtils.hasText(criteria.getKeyword())) {
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), "%" + criteria.getKeyword().toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("officialNumber")), "%" + criteria.getKeyword().toLowerCase() + "%")
                ));
            }
            if (criteria.getDocumentTypeId() != null) {
                predicates.add(cb.equal(root.get("documentTypeId"), criteria.getDocumentTypeId()));
            }
            if (criteria.getIssuingOrgId() != null) {
                predicates.add(cb.equal(root.get("issuingOrgId"), criteria.getIssuingOrgId()));
            }
            if (criteria.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("issueDate"), criteria.getFromDate()));
            }
            if (criteria.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("issueDate"), criteria.getToDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```
**Controller:** `GET /api/v1/documents/search` nhận các query params, phân trang và sắp xếp.

### Bước 2: Hoàn thiện Entity & Module Audit Log (UC19 - Theo core thầy)
```java
@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username; // Tên người thực hiện

    @Column(nullable = false, length = 50)
    private String action; // LOGIN, CREATE, UPDATE, DELETE, SIGN, PUBLISH, DOWNLOAD, VERIFY

    @Column(nullable = false, length = 50)
    private String resourceName; // DOCUMENT, USER, ORG_NODE, POSITION_ASSIGNMENT

    private Long resourceId; // ID của bản ghi bị tác động

    @Column(columnDefinition = "TEXT")
    private String oldValue; // JSON snapshot trạng thái trước

    @Column(columnDefinition = "TEXT")
    private String newValue; // JSON snapshot trạng thái sau

    @Column(length = 45)
    private String ipAddress; // IPv4 / IPv6

    @Column(length = 500)
    private String userAgent; // Trình duyệt / Client App

    @Column(length = 100)
    private String sessionId; // JWT JTI để truy vết phiên

    @Column(nullable = false)
    private Instant timestamp; // Thời điểm xảy ra sự kiện
}
```

**Xuất Báo Cáo Audit Log ra Excel (Apache POI):**
```xml
<!-- Thêm vào pom.xml -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

**Controller:** `AuditLogController`:
- `GET /api/v1/audit-logs`: Danh sách nhật ký (phân trang, lọc).
- `GET /api/v1/audit-logs/export`: Xuất Excel, Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- `GET /api/v1/audit-logs/{id}`: Chi tiết bản ghi kèm diff.

### Bước 3: Module Thông Báo In-app & Gửi Email Async (UC20)
**Cấu hình Spring Mail (`application.yml`):**
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**Viết `EmailService` không đồng bộ (`@Async`):**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Async
    @Override
    public void sendNotificationEmail(String toEmail, String subject,
                                      String templateName, Map<String, Object> model) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            Context context = new Context();
            context.setVariables(model);
            String htmlContent = templateEngine.process(templateName, context);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email tới {}: {}", toEmail, e.getMessage());
        }
    }
}
```

**Controller In-app Notification (`/api/v1/notifications`):**
- `GET /api/v1/notifications`: Lấy danh sách thông báo của tôi.
- `GET /api/v1/notifications/unread-count`: Đếm số thông báo chưa đọc.
- `PATCH /api/v1/notifications/{id}/read`: Đánh dấu 1 thông báo đã đọc.
- `PATCH /api/v1/notifications/read-all`: Đánh dấu toàn bộ đã đọc.

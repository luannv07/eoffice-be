package vn.edu.epu.eoffice_be.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {

    private Document document;

    @BeforeEach
    void setUp() {
        document = new Document();
    }

    @Test
    @DisplayName("Khởi tạo Document mặc định có status là DRAFT")
    void defaultStatus_shouldBeDraft() {
        assertEquals(DocumentStatus.DRAFT, document.getStatus());
        Document built = Document.builder().build();
        assertEquals(DocumentStatus.DRAFT, built.getStatus());
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    @DisplayName("setStatus(String): Gán trạng thái thành công với tất cả enum hợp lệ")
    void setStatus_withValidString_shouldSetCorrespondingStatus(DocumentStatus expectedStatus) {
        document.setStatus(expectedStatus.name());
        assertEquals(expectedStatus, document.getStatus());
    }

    @Test
    @DisplayName("setStatus(String): Bỏ qua khoảng trắng đầu cuối khi gán trạng thái")
    void setStatus_withWhitespaceAroundValidString_shouldTrimAndSet() {
        document.setStatus("  IN_REVIEW  ");
        assertEquals(DocumentStatus.IN_REVIEW, document.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_STATUS", "UNKNOWN", "OTHER", "WAITING_SIGN", "draft", "in_review"})
    @DisplayName("setStatus(String): Ném ngoại lệ IllegalArgumentException với trạng thái không xác định và KHÔNG ép về DRAFT")
    void setStatus_withInvalidString_shouldThrowIllegalArgumentExceptionAndNotDefaultToDraft(String invalidStatus) {
        document.setStatus(DocumentStatus.APPROVED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> document.setStatus(invalidStatus));
        assertTrue(ex.getMessage().contains("Unknown document status"), "Message should indicate unknown status");

        // Đảm bảo không bị âm thầm gán về DRAFT
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("setStatus(String): Ném ngoại lệ IllegalArgumentException khi chuỗi là null hoặc blank")
    void setStatus_withNullOrBlankString_shouldThrowIllegalArgumentException(String blankStatus) {
        document.setStatus(DocumentStatus.APPROVED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> document.setStatus(blankStatus));
        assertTrue(ex.getMessage().contains("Document status must not be null or blank"));

        // Đảm bảo không bị gán null vào field
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
    }

    @Test
    @DisplayName("setStatus(DocumentStatus): Giữ nguyên hành vi cho phép truyền DocumentStatus enum")
    void setStatus_withDocumentStatusEnum_shouldRetainExistingBehavior() {
        document.setStatus(DocumentStatus.PUBLISHED);
        assertEquals(DocumentStatus.PUBLISHED, document.getStatus());

        document.setStatus(DocumentStatus.REJECTED);
        assertEquals(DocumentStatus.REJECTED, document.getStatus());

        document.setStatus((DocumentStatus) null);
        assertNull(document.getStatus(), "Direct enum setter behavior should remain unchanged");
    }

    @Test
    @DisplayName("validateStatus: Cơ chế @PrePersist / @PreUpdate ngăn chặn lưu Document khi status là null")
    void validateStatus_whenStatusIsNull_shouldThrowIllegalStateException() throws Exception {
        document.setStatus((DocumentStatus) null);

        Method validateMethod = Document.class.getDeclaredMethod("validateStatus");
        validateMethod.setAccessible(true);

        Exception invocationException = assertThrows(Exception.class, () -> validateMethod.invoke(document));
        Throwable cause = invocationException.getCause();
        assertInstanceOf(IllegalStateException.class, cause);
        assertEquals("Document status cannot be null", cause.getMessage());
    }

    @Test
    @DisplayName("validateStatus: Thành công khi status khác null")
    void validateStatus_whenStatusIsNotNull_shouldNotThrow() throws Exception {
        document.setStatus(DocumentStatus.SUBMITTED);

        Method validateMethod = Document.class.getDeclaredMethod("validateStatus");
        validateMethod.setAccessible(true);

        assertDoesNotThrow(() -> validateMethod.invoke(document));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UTC",
            "Asia/Ho_Chi_Minh",
            "America/New_York",
            "Europe/London",
            "Asia/Tokyo",
            "Pacific/Honolulu",
            "Australia/Sydney"
    })
    @DisplayName("setIssueDate(LocalDate) & setEffectiveDate(LocalDate): Chuyển đổi ổn định không phụ thuộc vào JVM default timezone")
    void dateSetters_shouldProduceStableInstantAcrossDifferentJvmDefaultTimezones(String jvmTzId) {
        TimeZone originalJvmTz = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(jvmTzId));

            LocalDate issueDate = LocalDate.of(2026, 9, 13);
            LocalDate effectiveDate = LocalDate.of(2026, 10, 1);

            document.setIssueDate(issueDate);
            document.setEffectiveDate(effectiveDate);

            // Kỳ vọng thời điểm Instant cố định tương ứng với 00:00:00 tại Asia/Ho_Chi_Minh (UTC+7)
            Instant expectedIssueInstant = issueDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            Instant expectedEffectiveInstant = effectiveDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

            assertEquals(expectedIssueInstant, document.getIssueDate(),
                    "IssueDate Instant phải độc lập với JVM default timezone " + jvmTzId);
            assertEquals(expectedEffectiveInstant, document.getEffectiveDate(),
                    "EffectiveDate Instant phải độc lập với JVM default timezone " + jvmTzId);

            // Chuyển đổi ngược về LocalDate theo múi giờ nghiệp vụ luôn bảo toàn đúng ngày gốc
            assertEquals(issueDate, LocalDate.ofInstant(document.getIssueDate(), Document.getBusinessZone()));
            assertEquals(effectiveDate, LocalDate.ofInstant(document.getEffectiveDate(), Document.getBusinessZone()));
        } finally {
            TimeZone.setDefault(originalJvmTz);
        }
    }

    @Test
    @DisplayName("setIssueDate / setEffectiveDate với null: Gán giá trị null an toàn")
    void dateSetters_withNull_shouldSetNull() {
        document.setIssueDate((LocalDate) null);
        document.setEffectiveDate((LocalDate) null);
        assertNull(document.getIssueDate());
        assertNull(document.getEffectiveDate());

        document.setIssueDate((Instant) null);
        document.setEffectiveDate((Instant) null);
        assertNull(document.getIssueDate());
        assertNull(document.getEffectiveDate());
    }

    @Test
    @DisplayName("setIssueDate(Instant) / setEffectiveDate(Instant): Giữ nguyên giá trị Instant truyền vào")
    void dateSetters_withInstant_shouldAssignDirectly() {
        Instant now = Instant.parse("2026-09-13T10:15:30Z");
        document.setIssueDate(now);
        document.setEffectiveDate(now);

        assertEquals(now, document.getIssueDate());
        assertEquals(now, document.getEffectiveDate());
    }

    @Test
    @DisplayName("getBusinessZone: Múi giờ nghiệp vụ mặc định là hằng số bất biến Asia/Ho_Chi_Minh")
    void getBusinessZone_shouldBeImmutableDefaultBusinessZone() {
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), Document.DEFAULT_BUSINESS_ZONE);
        assertEquals(Document.DEFAULT_BUSINESS_ZONE, Document.getBusinessZone());

        LocalDate date = LocalDate.of(2026, 9, 13);
        document.setIssueDate(date);
        assertEquals(date.atStartOfDay(Document.DEFAULT_BUSINESS_ZONE).toInstant(), document.getIssueDate());
    }

    @Test
    @DisplayName("setIssueDate(LocalDate, ZoneId) / setEffectiveDate(LocalDate, ZoneId): Hỗ trợ truyền múi giờ tường minh")
    void dateSetters_withExplicitZoneId_shouldUseSpecifiedZone() {
        LocalDate date = LocalDate.of(2026, 9, 13);
        ZoneId utc = ZoneId.of("UTC");

        document.setIssueDate(date, utc);
        document.setEffectiveDate(date, utc);

        assertEquals(Instant.parse("2026-09-13T00:00:00Z"), document.getIssueDate());
        assertEquals(Instant.parse("2026-09-13T00:00:00Z"), document.getEffectiveDate());
    }
}

package vn.edu.epu.eoffice_be.common.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("DocumentStatus: description contract cho APPROVED, PUBLISHED và REJECTED phải được giữ ổn định")
    void testDescriptionContract() {
        assertEquals("Đã phê duyệt", DocumentStatus.APPROVED.getDescription());
        assertEquals("Đã ban hành chính thức", DocumentStatus.PUBLISHED.getDescription());
        assertEquals("Bị từ chối phê duyệt", DocumentStatus.REJECTED.getDescription());
        assertEquals("Dự thảo / Bản nháp", DocumentStatus.DRAFT.getDescription());
        assertEquals("Đang thẩm định & ký nháy", DocumentStatus.IN_REVIEW.getDescription());
        assertEquals("Chờ lãnh đạo phê duyệt", DocumentStatus.PENDING_APPROVAL.getDescription());
    }

    @Test
    @DisplayName("DocumentStatus: Mọi giá trị enum đều có mô tả không rỗng")
    void testAllDescriptionsNonNull() {
        for (DocumentStatus status : DocumentStatus.values()) {
            assertNotNull(status.getDescription());
            assertFalse(status.getDescription().isBlank());
        }
    }

    @Test
    @DisplayName("DocumentStatus: Tuần tự hóa JSON sử dụng chuỗi tên enum chứ không dùng ordinal")
    void testJsonSerializationMatchesName() throws Exception {
        for (DocumentStatus status : DocumentStatus.values()) {
            String json = objectMapper.writeValueAsString(status);
            assertEquals("\"" + status.name() + "\"", json);
            DocumentStatus deserialized = objectMapper.readValue(json, DocumentStatus.class);
            assertEquals(status, deserialized);
        }
    }
}

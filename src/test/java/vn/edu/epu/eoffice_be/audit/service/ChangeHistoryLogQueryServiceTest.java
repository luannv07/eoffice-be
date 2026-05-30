package vn.edu.epu.eoffice_be.audit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.edu.epu.eoffice_be.audit.dto.request.ChangeHistoryLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.ChangeHistoryLogResponse;
import vn.edu.epu.eoffice_be.audit.entity.ChangeHistoryLog;
import vn.edu.epu.eoffice_be.audit.repository.ChangeHistoryLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeHistoryLogQueryServiceTest {

    @Mock
    private ChangeHistoryLogRepository repository;

    @InjectMocks
    private ChangeHistoryLogQueryService service;

    @Test
    void shouldAllowActionFilterWhenTableNameAndObjectIdPresent() {
        ChangeHistoryLogFilterRequest request = new ChangeHistoryLogFilterRequest();
        request.setTableName("users");
        request.setObjectId("123");

        boolean allowed = service.shouldApplyActionFilter("users", "123", null, request);

        assertTrue(allowed);
    }

    @Test
    void shouldAllowActionFilterWhenChangedByAndTimeRangePresent() {
        ChangeHistoryLogFilterRequest request = new ChangeHistoryLogFilterRequest();
        request.setChangedBy("admin");
        request.setChangedFrom(Instant.parse("2026-05-01T00:00:00Z"));

        boolean allowed = service.shouldApplyActionFilter(null, null, "admin", request);

        assertTrue(allowed);
    }

    @Test
    void shouldNotAllowActionFilterWhenNoLeadingIndexFilter() {
        ChangeHistoryLogFilterRequest request = new ChangeHistoryLogFilterRequest();
        request.setAction("UPDATE");

        boolean allowed = service.shouldApplyActionFilter(null, null, null, request);

        assertFalse(allowed);
    }

    @Test
    void filterShouldReturnMappedPage() {
        ChangeHistoryLog entity = ChangeHistoryLog.builder()
                .id(UUID.randomUUID())
                .tableName("users")
                .objectId("1")
                .action("UPDATE")
                .changedBy("admin")
                .changedAt(Instant.parse("2026-05-30T10:00:00Z"))
                .sourceService("eoffice-be")
                .beforeData("{\"name\":\"A\"}")
                .afterData("{\"name\":\"B\"}")
                .changedFields("[\"name\"]")
                .build();

        when(repository.findAll(ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<ChangeHistoryLog>>any(), ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ChangeHistoryLogResponse> result = service.filter(new ChangeHistoryLogFilterRequest());

        assertEquals(1, result.getTotalElements());
        assertEquals("users", result.getContent().getFirst().getTableName());
        assertEquals("UPDATE", result.getContent().getFirst().getAction());
    }
}

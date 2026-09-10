package vn.edu.epu.eoffice_be.audit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.edu.epu.eoffice_be.audit.dto.request.UserAuditLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.UserAuditLogResponse;
import vn.edu.epu.eoffice_be.audit.entity.UserAuditLog;
import vn.edu.epu.eoffice_be.audit.repository.UserAuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuditLogQueryServiceTest {

	@Mock
	private UserAuditLogRepository repository;

	@InjectMocks
	private UserAuditLogQueryService service;

	@Test
	void filterShouldReturnMappedPage() {
		UserAuditLog entity = UserAuditLog.builder()
						.id(UUID.randomUUID())
						.username("admin")
						.userAgent("Mozilla")
						.clientIp("127.0.0.1")
						.requestUri("/audit-logs")
						.methodName("filter")
						.executionTimeMs(25L)
						.paramContent("{}")
						.createdDate(Instant.parse("2026-05-30T10:00:00Z"))
						.build();

		when(repository.findAll(ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserAuditLog>>any(), ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
						.thenReturn(new PageImpl<>(List.of(entity)));

		Page<UserAuditLogResponse> result = service.filter(new UserAuditLogFilterRequest());

		assertEquals(1, result.getTotalElements());
		assertEquals("admin", result.getContent().getFirst().getUsername());
		assertEquals("/audit-logs", result.getContent().getFirst().getRequestUri());
	}
}

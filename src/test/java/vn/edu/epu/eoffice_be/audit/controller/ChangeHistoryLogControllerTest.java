package vn.edu.epu.eoffice_be.audit.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.edu.epu.eoffice_be.audit.dto.request.ChangeHistoryLogFilterRequest;
import vn.edu.epu.eoffice_be.audit.dto.response.ChangeHistoryLogResponse;
import vn.edu.epu.eoffice_be.audit.service.ChangeHistoryLogQueryService;
import vn.edu.epu.eoffice_be.common.dto.BaseResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeHistoryLogControllerTest {

	@Mock
	private ChangeHistoryLogQueryService service;

	@InjectMocks
	private ChangeHistoryLogController controller;

	@Test
	void filterShouldReturnSuccessResponse() {
		Page<ChangeHistoryLogResponse> page = new PageImpl<>(List.of(
						ChangeHistoryLogResponse.builder().tableName("users").build()
		));
		when(service.filter(org.mockito.ArgumentMatchers.any(ChangeHistoryLogFilterRequest.class))).thenReturn(page);

		BaseResponse<?> response = controller.filter(new ChangeHistoryLogFilterRequest());

		assertTrue(response.isSuccess());
		Page<?> data = (Page<?>) response.getData();
		assertEquals(1, data.getTotalElements());
	}
}

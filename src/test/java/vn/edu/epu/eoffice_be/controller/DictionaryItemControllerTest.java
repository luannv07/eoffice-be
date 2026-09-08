package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemBatchDeleteRequest;
import vn.edu.epu.eoffice_be.dto.request.DictionaryItemCreateRequest;
import vn.edu.epu.eoffice_be.dto.response.DictionaryItemResponse;
import vn.edu.epu.eoffice_be.service.DictionaryItemService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DictionaryItemControllerTest {

	@Mock
	DictionaryItemService dictionaryItemService;

	MockMvc mockMvc;
	ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
						.standaloneSetup(new DictionaryItemController(dictionaryItemService))
						.setControllerAdvice(new GlobalExceptionHandler())
						.build();
	}

	private DictionaryItemResponse sample(Long id) {
		return DictionaryItemResponse.builder()
						.id(id)
						.groupCd("TEST_GROUP")
						.itemCd("CD_" + id)
						.nameVn("Tên " + id)
						.nameEn("Name " + id)
						.displayOrder(1)
						.activeFlg(true)
						.build();
	}

	@Test
	void getAllShouldReturnPagedData() throws Exception {
		when(dictionaryItemService.getAll(any())).thenReturn(
						new PageImpl<>(List.of(sample(1L), sample(2L)), PageRequest.of(0, 10), 2));

		mockMvc.perform(get("/dictionary-items"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true))
						.andExpect(jsonPath("$.data.content.length()").value(2))
						.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void getDetailShouldReturnItem() throws Exception {
		when(dictionaryItemService.getDetail(5L)).thenReturn(sample(5L));

		mockMvc.perform(get("/dictionary-items/5"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.itemCd").value("CD_5"));
	}

	@Test
	void getDetailShouldReturn404WhenNotFound() throws Exception {
		when(dictionaryItemService.getDetail(404L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mockMvc.perform(get("/dictionary-items/404"))
						.andExpect(status().isNotFound());
	}

	@Test
	void createShouldReturnCreatedItem() throws Exception {
		DictionaryItemCreateRequest request = new DictionaryItemCreateRequest();
		request.setGroupCd("TEST_GROUP");
		request.setItemCd("NEW_CD");
		request.setNameVn("Tên mới");
		request.setNameEn("New name");

		when(dictionaryItemService.create(any(DictionaryItemCreateRequest.class))).thenReturn(sample(9L));

		mockMvc.perform(post("/dictionary-items")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.id").value(9))
						.andExpect(jsonPath("$.data.itemCd").value("CD_9"));
	}

	@Test
	void createShouldReturn409WhenAlreadyExists() throws Exception {
		when(dictionaryItemService.create(any(DictionaryItemCreateRequest.class)))
						.thenThrow(new BusinessException(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS));

		mockMvc.perform(post("/dictionary-items")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"groupCd\":\"G\",\"itemCd\":\"C\",\"nameVn\":\"n\",\"nameEn\":\"e\"}"))
						.andExpect(status().isConflict());
	}

	@Test
	void updateShouldReturnUpdatedItem() throws Exception {
		when(dictionaryItemService.update(eq(7L), any())).thenReturn(sample(7L));

		mockMvc.perform(put("/dictionary-items/7")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"nameVn\":\"Tên sửa\"}"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.id").value(7));
	}

	@Test
	void deleteShouldReturnSuccess() throws Exception {
		mockMvc.perform(delete("/dictionary-items/3"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true));

		verify(dictionaryItemService).delete(3L);
	}

	@Test
	void batchDeleteShouldReturnDeletedCount() throws Exception {
		DictionaryItemBatchDeleteRequest request = new DictionaryItemBatchDeleteRequest();
		request.setIds(List.of(1L, 2L, 3L));
		when(dictionaryItemService.batchDelete(any(DictionaryItemBatchDeleteRequest.class))).thenReturn(2L);

		mockMvc.perform(post("/dictionary-items/batch-delete")
										.contentType(MediaType.APPLICATION_JSON)
										.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data").value(2));
	}

	@Test
	void lookupByGroupsShouldReturnItemsSorted() throws Exception {
		when(dictionaryItemService.lookupByGroups(List.of("USER_STATUS_CD", "EMPLOYMENT_STATUS_CD")))
						.thenReturn(List.of(
										sample(1001L),
										sample(1051L)));

		mockMvc.perform(get("/dictionary-items/lookup-by-groups")
										.param("groups", "USER_STATUS_CD,EMPLOYMENT_STATUS_CD"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true))
						.andExpect(jsonPath("$.data.length()").value(2))
						.andExpect(jsonPath("$.data[0].id").value(1001))
						.andExpect(jsonPath("$.data[1].id").value(1051));
	}

	@Test
	void lookupByGroupsShouldReturn400WhenMissing() throws Exception {
		when(dictionaryItemService.lookupByGroups(null))
						.thenThrow(new BusinessException(ErrorCode.BAD_REQUEST));

		mockMvc.perform(get("/dictionary-items/lookup-by-groups"))
						.andExpect(status().isBadRequest());
	}
}

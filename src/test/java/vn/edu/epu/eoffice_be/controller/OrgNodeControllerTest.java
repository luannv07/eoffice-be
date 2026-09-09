package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.OrgNodeUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.OrgNodeResponse;
import vn.edu.epu.eoffice_be.service.OrgNodeService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrgNodeControllerTest {

	@Mock
	OrgNodeService orgNodeService;

	MockMvc mockMvc;

	ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setup() {
		mockMvc = MockMvcBuilders.standaloneSetup(new OrgNodeController(orgNodeService))
						.setControllerAdvice(new GlobalExceptionHandler())
						.build();
	}

	private OrgNodeResponse node(Long id, Long parentId) {
		return OrgNodeResponse.builder()
						.id(id)
						.parentId(parentId)
						.code("C" + id)
						.nameVn("Tên " + id)
						.nameEn("Name " + id)
						.typeCd("FACULTY")
						.orgOrder(0)
						.children(new ArrayList<>())
						.build();
	}

	@Test
	void getRootsShouldReturnRootList() throws Exception {
		when(orgNodeService.getRoots()).thenReturn(List.of(node(1L, null), node(2L, null)));

		mockMvc.perform(get("/org-nodes/roots"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.success").value(true))
						.andExpect(jsonPath("$.data.length()").value(2));

		verify(orgNodeService).getRoots();
	}

	@Test
	void getChildrenShouldReturnChildList() throws Exception {
		when(orgNodeService.getChildren(5L)).thenReturn(List.of(node(51L, 5L)));

		mockMvc.perform(get("/org-nodes/5/children"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data[0].parentId").value(5));

		verify(orgNodeService).getChildren(5L);
	}

	@Test
	void getDetailShouldReturnNode() throws Exception {
		when(orgNodeService.getDetail(7L)).thenReturn(node(7L, null));

		mockMvc.perform(get("/org-nodes/7"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.id").value(7));

		verify(orgNodeService).getDetail(7L);
	}

	@Test
	void getDetailShouldReturnNotFoundWhenMissing() throws Exception {
		when(orgNodeService.getDetail(404L))
						.thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		mockMvc.perform(get("/org-nodes/404"))
						.andExpect(status().isNotFound())
						.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void getAllShouldReturnTree() throws Exception {
		when(orgNodeService.getAll(any())).thenReturn(List.of(node(1L, null)));

		mockMvc.perform(get("/org-nodes"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.length()").value(1));

		verify(orgNodeService).getAll(any());
	}

	@Test
	void createShouldInvokeService() throws Exception {
		OrgNodeCreateRequest request = new OrgNodeCreateRequest();
		request.setCode("NEW");
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setTypeCd("FACULTY");

		mockMvc.perform(post("/org-nodes")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk());

		verify(orgNodeService).create(any());
	}

	@Test
	void createShouldReturnConflictWhenCodeExists() throws Exception {
		OrgNodeCreateRequest request = new OrgNodeCreateRequest();
		request.setCode("DUP");
		request.setNameVn("Mới");
		request.setNameEn("New");
		request.setTypeCd("FACULTY");
		doThrow(new BusinessException(ErrorCode.ORG_NODE_CODE_EXISTS))
						.when(orgNodeService).create(any());

		mockMvc.perform(post("/org-nodes")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isConflict());
	}

	@Test
	void updateShouldInvokeServiceAndReturnList() throws Exception {
		OrgNodeUpdateRequest request = new OrgNodeUpdateRequest();
		request.setParentId(1001L);
		request.setNameVn("Mới");
		request.setNameEn("New");
		when(orgNodeService.update(any())).thenReturn(List.of(node(7L, null)));

		mockMvc.perform(put("/org-nodes/7")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(request)))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.data.length()").value(1));

		verify(orgNodeService).update(any());
	}

	@Test
	void deleteShouldInvokeService() throws Exception {
		mockMvc.perform(delete("/org-nodes/3"))
						.andExpect(status().isOk());

		verify(orgNodeService).delete(3L);
	}
}

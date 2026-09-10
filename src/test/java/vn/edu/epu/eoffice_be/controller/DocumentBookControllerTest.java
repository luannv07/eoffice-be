package vn.edu.epu.eoffice_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentBookResponse;
import vn.edu.epu.eoffice_be.service.DocumentBookService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentBookControllerTest {

	@Mock
	DocumentBookService documentBookService;

	MockMvc mockMvc;
	ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());

		mockMvc = MockMvcBuilders
				.standaloneSetup(new DocumentBookController(documentBookService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void getAll_Returns200() throws Exception {
		DocumentBookResponse res = DocumentBookResponse.builder()
				.id(1L)
				.bookCode("SVD-QD-2026")
				.build();
		when(documentBookService.getAll(any(DocumentBookFilterRequest.class)))
				.thenReturn(new PageImpl<>(List.of(res), PageRequest.of(0, 10), 1));

		mockMvc.perform(get("/document-books?year=2026"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content[0].bookCode").value("SVD-QD-2026"));
	}

	@Test
	void getDetail_Returns200() throws Exception {
		DocumentBookResponse res = DocumentBookResponse.builder()
				.id(1L)
				.bookCode("SVD-QD-2026")
				.build();
		when(documentBookService.getDetail(1L)).thenReturn(res);

		mockMvc.perform(get("/document-books/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(1));
	}

	@Test
	void create_ValidRequest_Returns200() throws Exception {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-QD-DHEPU-2026");
		req.setBookName("Sổ Quyết định");
		req.setYear(2026);
		req.setBookLevel(1);
		req.setDocumentTypeId(3001L);
		req.setOrgNodeId(1001L);
		req.setNumberPattern("{NUM}/QĐ-ĐHEPU");

		DocumentBookResponse res = DocumentBookResponse.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-2026")
				.build();
		when(documentBookService.create(any(DocumentBookCreateRequest.class))).thenReturn(res);

		mockMvc.perform(post("/document-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.bookCode").value("SVD-QD-DHEPU-2026"));
	}

	@Test
	void create_InvalidPattern_Returns400() throws Exception {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-QD-DHEPU-2026");
		req.setBookName("Sổ Quyết định");
		req.setYear(2026);
		req.setBookLevel(1);
		req.setDocumentTypeId(3001L);
		req.setOrgNodeId(1001L);
		req.setNumberPattern("125/QĐ-ĐHEPU"); // Thiếu {NUM}

		mockMvc.perform(post("/document-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void update_ValidRequest_Returns200() throws Exception {
		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setBookName("Sổ Quyết định mới");
		req.setNumberPattern("{NUM}/QĐ-BGH");

		DocumentBookResponse res = DocumentBookResponse.builder()
				.id(1L)
				.bookName("Sổ Quyết định mới")
				.build();
		when(documentBookService.update(eq(1L), any(DocumentBookUpdateRequest.class))).thenReturn(res);

		mockMvc.perform(put("/document-books/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.bookName").value("Sổ Quyết định mới"));
	}

	@Test
	void close_Returns200() throws Exception {
		mockMvc.perform(put("/document-books/1/close"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(documentBookService).close(1L);
	}

	@Test
	void delete_Returns200() throws Exception {
		mockMvc.perform(delete("/document-books/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(documentBookService).delete(1L);
	}

	@Test
	void assignNumber_Returns200() throws Exception {
		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));

		AssignDocumentNumberResponse res = AssignDocumentNumberResponse.builder()
				.documentNumber("1/QĐ-ĐHEPU")
				.bookId(1L)
				.currentNumber(1)
				.effectiveDate(LocalDate.of(2026, 9, 10))
				.build();
		when(documentBookService.assignNumber(any(AssignDocumentNumberRequest.class))).thenReturn(res);

		mockMvc.perform(post("/document-books/assign-number")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.documentNumber").value("1/QĐ-ĐHEPU"))
				.andExpect(jsonPath("$.data.currentNumber").value(1));
	}
}

package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentBookResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.service.impl.DocumentBookServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBookServiceImplTest {

	@Mock
	DocumentBookRepository documentBookRepository;

	@Mock
	DictionaryItemRepository dictionaryItemRepository;

	@Mock
	OrgNodeRepository orgNodeRepository;

	@InjectMocks
	DocumentBookServiceImpl documentBookService;

	private DictionaryItem docType;
	private OrgNode orgNode;
	private DocumentBook book;

	@BeforeEach
	void setUp() {
		docType = DictionaryItem.builder()
				.id(3001L)
				.groupCd("DOCUMENT_TYPE")
				.itemCd("QD")
				.nameVn("Quyết định")
				.nameEn("Decision")
				.build();

		orgNode = OrgNode.builder()
				.id(1001L)
				.code("DHEPU")
				.nameVn("Trường Đại học Điện Lực")
				.nameEn("Electric Power University")
				.build();

		book = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-2026")
				.bookName("Sổ Quyết định cấp Trường năm 2026")
				.year(2026)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(orgNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.currentNumber(0)
				.isClosed(false)
				.deleteFlg(false)
				.build();
	}

	@Test
	void create_Success() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-QD-DHEPU-2026");
		req.setBookName("Sổ Quyết định cấp Trường năm 2026");
		req.setYear(2026);
		req.setBookLevel(1);
		req.setDocumentTypeId(3001L);
		req.setOrgNodeId(1001L);
		req.setNumberPattern("{NUM}/QĐ-ĐHEPU");

		when(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("SVD-QD-DHEPU-2026")).thenReturn(false);
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(2026, 1001L, 3001L)).thenReturn(false);
		when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(3001L)).thenReturn(Optional.of(docType));
		when(orgNodeRepository.findByIdAndDeleteFlgFalse(1001L)).thenReturn(Optional.of(orgNode));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> {
			DocumentBook b = inv.getArgument(0);
			b.setId(10L);
			return b;
		});

		DocumentBookResponse res = documentBookService.create(req);
		assertNotNull(res);
		assertEquals(10L, res.getId());
		assertEquals("SVD-QD-DHEPU-2026", res.getBookCode());
		assertEquals(0, res.getCurrentNumber());
		assertEquals("Quyết định", res.getDocumentTypeName());
		assertEquals("Trường Đại học Điện Lực", res.getOrgNodeName());
	}

	@Test
	void create_DuplicateBookCode_ThrowsException() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-QD-DHEPU-2026");

		when(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("SVD-QD-DHEPU-2026")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.create(req));
		assertEquals(ErrorCode.DOCUMENT_BOOK_CODE_EXISTS, ex.getErrorCode());
	}

	@Test
	void create_DuplicateYearOrgType_ThrowsException() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-QD-DHEPU-2026-NEW");
		req.setYear(2026);
		req.setOrgNodeId(1001L);
		req.setDocumentTypeId(3001L);

		when(documentBookRepository.existsByBookCodeAndDeleteFlgFalse(any())).thenReturn(false);
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(2026, 1001L, 3001L)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.create(req));
		assertEquals(ErrorCode.DOCUMENT_BOOK_DUPLICATE, ex.getErrorCode());
	}

	@Test
	void create_DocTypeWrongGroup_ThrowsException() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-TEST-2026");
		req.setYear(2026);
		req.setOrgNodeId(1001L);
		req.setDocumentTypeId(1001L);

		DictionaryItem wrongGroupItem = DictionaryItem.builder()
				.id(1001L)
				.groupCd("USER_STATUS_CD")
				.itemCd("ACTIVE")
				.build();

		when(documentBookRepository.existsByBookCodeAndDeleteFlgFalse(any())).thenReturn(false);
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(any(), any(), any())).thenReturn(false);
		when(dictionaryItemRepository.findByIdAndDeleteFlgFalse(1001L)).thenReturn(Optional.of(wrongGroupItem));

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.create(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
	}

	@Test
	void update_Success() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));

		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setBookName("Tên sổ mới");
		req.setNumberPattern("{NUM}/QĐ-BGH");

		DocumentBookResponse res = documentBookService.update(1L, req);
		assertEquals("Tên sổ mới", res.getBookName());
		assertEquals("{NUM}/QĐ-BGH", res.getNumberPattern());
	}

	@Test
	void update_ClosedBook_ThrowsException() {
		book.setIsClosed(true);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));

		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setBookName("Tên mới");

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.update(1L, req));
		assertEquals(ErrorCode.DOCUMENT_BOOK_CLOSED, ex.getErrorCode());
	}

	@Test
	void close_Success() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		documentBookService.close(1L);
		assertTrue(book.getIsClosed());
	}

	@Test
	void delete_Success() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		documentBookService.delete(1L);
		assertTrue(book.isDeleteFlg());
	}

	@Test
	void assignNumber_Success_IncrementsCurrentNumber() {
		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));
		req.setDocumentId(5001L);

		AssignDocumentNumberResponse res = documentBookService.assignNumber(req);

		assertNotNull(res);
		assertEquals("1/QĐ-ĐHEPU", res.getDocumentNumber());
		assertEquals(1, res.getCurrentNumber());
		assertEquals(1L, res.getBookId());
		assertEquals(5001L, res.getDocumentId());
		assertEquals(LocalDate.of(2026, 9, 10), res.getEffectiveDate());
		assertEquals(1, book.getCurrentNumber());
	}

	@Test
	void assignNumber_ClosedBook_ThrowsException() {
		book.setIsClosed(true);
		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.now());

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.DOCUMENT_BOOK_CLOSED, ex.getErrorCode());
	}

	@Test
	void getAll_Success() {
		DocumentBookFilterRequest req = new DocumentBookFilterRequest();
		req.setYear(2026);
		req.setBookLevel(1);

		when(documentBookRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(book)));

		var page = documentBookService.getAll(req);
		assertEquals(1, page.getTotalElements());
		assertEquals("SVD-QD-DHEPU-2026", page.getContent().get(0).getBookCode());
	}
}

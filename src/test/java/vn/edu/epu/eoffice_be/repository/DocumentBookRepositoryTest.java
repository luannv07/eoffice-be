package vn.edu.epu.eoffice_be.repository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.OrgNode;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentBookRepositoryTest {

	@Autowired
	DocumentBookRepository documentBookRepository;

	@Autowired
	DictionaryItemRepository dictionaryItemRepository;

	@Autowired
	OrgNodeRepository orgNodeRepository;

	private Validator validator;

	@BeforeEach
	void setUp() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	void testNumberPatternRegexValidation() {
		// Valid patterns
		assertTrue(isValidPattern("{NUM}/QĐ-ĐHEPU"));
		assertTrue(isValidPattern("{NUM}/TB-CNTT"));
		assertTrue(isValidPattern("{NUM}/NQ-DHEPU"));
		assertTrue(isValidPattern("{NUM}/CV-K.CNTT"));

		// Invalid patterns
		assertFalse(isValidPattern("{NUM}"));                  // Thiếu mã loại và đơn vị
		assertFalse(isValidPattern("125/QĐ-ĐHEPU"));           // Thiếu placeholder {NUM}
		assertFalse(isValidPattern("{NUM}-QD"));               // Thiếu dấu gạch chéo phân cách
		assertFalse(isValidPattern(""));                       // Rỗng
	}

	private boolean isValidPattern(String pattern) {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-TEST-2026");
		req.setBookName("Sổ test");
		req.setYear(2026);
		req.setBookLevel(1);
		req.setDocumentTypeId(3001L);
		req.setOrgNodeId(1L);
		req.setNumberPattern(pattern);

		var violations = validator.validateProperty(req, "numberPattern");
		return violations.isEmpty();
	}

	@Test
	void testDocumentBookRepositoryCrudAndLock() {
		DictionaryItem docType = dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse("DOCUMENT_TYPE", "QD")
				.orElseThrow();

		OrgNode rootNode = orgNodeRepository.findByCodeAndDeleteFlgFalse("EPU")
				.orElseGet(() -> orgNodeRepository.save(OrgNode.builder()
						.code("EPU")
						.nameVn("Trường Đại học Điện Lực")
						.nameEn("Electric Power University")
						.typeCd("UNIVERSITY")
						.build()));

		DocumentBook book = DocumentBook.builder()
				.bookCode("SVD-QD-EPU-2026")
				.bookName("Sổ Quyết định cấp Trường năm 2026")
				.year(2026)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(rootNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.currentNumber(0)
				.isClosed(false)
				.deleteFlg(false)
				.build();

		DocumentBook saved = documentBookRepository.saveAndFlush(book);
		assertNotNull(saved.getId());

		assertTrue(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("SVD-QD-EPU-2026"));
		assertFalse(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("UNKNOWN"));

		assertTrue(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
				2026, rootNode.getId(), docType.getId()));

		var found = documentBookRepository.findByIdForUpdate(saved.getId());
		assertTrue(found.isPresent());
		assertEquals("SVD-QD-EPU-2026", found.get().getBookCode());
		assertEquals(0, found.get().getCurrentNumber());
	}

	@Test
	void testDocumentBookFilterRequestPageable() {
		DocumentBookFilterRequest req = new DocumentBookFilterRequest();
		req.setSortBy("year");
		var pageable = req.pageable();
		assertNotNull(pageable);
		assertEquals(0, pageable.getPageNumber());
		assertEquals(10, pageable.getPageSize());
	}
}

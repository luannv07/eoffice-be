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
		// Valid patterns: exactly {NUM}/[MÃ_LOẠI]-[MÃ_ĐƠN_VỊ]
		assertTrue(isValidPattern("{NUM}/QĐ-ĐHEPU"));
		assertTrue(isValidPattern("{NUM}/TB-CNTT"));
		assertTrue(isValidPattern("{NUM}/NQ-DHEPU"));

		// Invalid patterns
		assertFalse(isValidPattern("{NUM}/CV-K.CNTT"));       // Chứa dấu chấm phân cách
		assertFalse(isValidPattern("{NUM}/QĐ/ĐHEPU"));        // Phân cách dấu gạch chéo
		assertFalse(isValidPattern("{NUM}/QĐ.ĐHEPU"));        // Phân cách dấu chấm
		assertFalse(isValidPattern("{NUM}/QĐ-ĐHEPU-2026"));   // Quá 2 segments
		assertFalse(isValidPattern("{NUM}"));                 // Thiếu mã loại và đơn vị
		assertFalse(isValidPattern("125/QĐ-ĐHEPU"));          // Thiếu placeholder {NUM}
		assertFalse(isValidPattern("{NUM}-QD"));              // Thiếu dấu gạch chéo phân cách
		assertFalse(isValidPattern(""));                      // Rỗng
	}

	@Test
	void testForeignKeyPositiveValidation() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-TEST-2026");
		req.setBookName("Sổ test");
		req.setYear(2026);
		req.setBookLevel(1);
		req.setNumberPattern("{NUM}/QĐ-ĐHEPU");

		// documentTypeId = 0 hoặc -1
		req.setDocumentTypeId(0L);
		req.setOrgNodeId(1L);
		assertFalse(validator.validateProperty(req, "documentTypeId").isEmpty());

		req.setDocumentTypeId(-1L);
		assertFalse(validator.validateProperty(req, "documentTypeId").isEmpty());

		// orgNodeId = 0 hoặc -1
		req.setDocumentTypeId(3001L);
		req.setOrgNodeId(0L);
		assertFalse(validator.validateProperty(req, "orgNodeId").isEmpty());

		req.setOrgNodeId(-1L);
		assertFalse(validator.validateProperty(req, "orgNodeId").isEmpty());

		// Positive validation: ID dương hợp lệ
		req.setDocumentTypeId(1L);
		req.setOrgNodeId(1L);
		assertTrue(validator.validateProperty(req, "documentTypeId").isEmpty());
		assertTrue(validator.validateProperty(req, "orgNodeId").isEmpty());
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
				.bookCode("SVD-QD-TEST-UNIQUE-2099")
				.bookName("Sổ Quyết định test 2099")
				.year(2099)
				.bookLevel(DocumentBook.LEVEL_INSTITUTION)
				.documentType(docType)
				.orgNode(rootNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.currentNumber(0)
				.isClosed(false)
				.deleteFlg(false)
				.build();

		DocumentBook saved = documentBookRepository.saveAndFlush(book);
		assertNotNull(saved.getId());

		assertTrue(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("SVD-QD-TEST-UNIQUE-2099"));
		assertFalse(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("UNKNOWN"));

		assertTrue(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
				2099, rootNode.getId(), docType.getId()));

		var found = documentBookRepository.findByIdForUpdate(saved.getId());
		assertTrue(found.isPresent());
		assertEquals("SVD-QD-TEST-UNIQUE-2099", found.get().getBookCode());
		assertEquals(0, found.get().getCurrentNumber());
	}

	@Test
	void testMultipleInsertsDedicatedSequence() {
		DictionaryItem docType = dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse("DOCUMENT_TYPE", "QD")
				.orElseThrow();

		OrgNode rootNode = orgNodeRepository.findByCodeAndDeleteFlgFalse("EPU")
				.orElseThrow();

		DocumentBook book1 = DocumentBook.builder()
				.bookCode("SVD-SEQ-TEST-1")
				.bookName("Sổ seq 1")
				.year(2090)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(rootNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.build();

		DocumentBook book2 = DocumentBook.builder()
				.bookCode("SVD-SEQ-TEST-2")
				.bookName("Sổ seq 2")
				.year(2091)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(rootNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.build();

		DocumentBook saved1 = documentBookRepository.saveAndFlush(book1);
		DocumentBook saved2 = documentBookRepository.saveAndFlush(book2);

		assertNotNull(saved1.getId());
		assertNotNull(saved2.getId());
		assertNotEquals(saved1.getId(), saved2.getId());
		// Defaults from @PrePersist
		assertEquals(0, saved1.getCurrentNumber());
		assertFalse(saved1.getIsClosed());
		assertEquals(0, saved2.getCurrentNumber());
		assertFalse(saved2.getIsClosed());
	}

	@Test
	void testDocumentBookFilterRequestPageable() {
		DocumentBookFilterRequest req = new DocumentBookFilterRequest();
		req.setSortBy("year");
		var pageable = req.pageable();
		assertNotNull(pageable);
		assertEquals(0, pageable.getPageNumber());
		assertEquals(10, pageable.getPageSize());
		// Kiểm tra tie-breaker id DESC luôn có mặt và sắp xếp giảm dần
		var idOrder = pageable.getSort().getOrderFor("id");
		assertNotNull(idOrder);
		assertTrue(idOrder.isDescending());
	}
}

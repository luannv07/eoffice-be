package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentBookResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.impl.DocumentBookServiceImpl;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository.DrafterName;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
import java.util.Collection;
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

	@Mock
	DocumentRepository documentRepository;

	@Mock
	vn.edu.epu.eoffice_be.security.pki.PdfSigner pdfSigner;

	@Mock
	vn.edu.epu.eoffice_be.service.FileStorageService fileStorageService;

	@Mock
	vn.edu.epu.eoffice_be.storage.FileStorageService unifiedStorageService;

	@Mock
	vn.edu.epu.eoffice_be.repository.UserLoginRepository userLoginRepository;

	@Mock
	vn.edu.epu.eoffice_be.repository.UserInfoRepository userInfoRepository;

	@Mock
	vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository documentWorkflowLogRepository;

	@Mock
	vn.edu.epu.eoffice_be.repository.DocumentAttachmentRepository documentAttachmentRepository;

	@Mock
	vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService documentPdfStorageService;

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

		UserLogin clerk = UserLogin.builder()
				.id(3003L)
				.username("vanthu")
				.role(Role.CLERK)
				.build();
		JwtUserPrincipal principal = new JwtUserPrincipal(3003L, "vanthu", "CLERK", true, List.of("ROLE_CLERK"));
		Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_CLERK")));
		SecurityContextHolder.getContext().setAuthentication(auth);
		lenient().when(userLoginRepository.findByIdAndDeleteFlgFalse(3003L)).thenReturn(Optional.of(clerk));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

    @ParameterizedTest
    @EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void administratorCannotAllocateNumberBeforeBookLockOrPdfAccess(Role role) {
        when(userLoginRepository.findByIdAndDeleteFlgFalse(3003L)).thenReturn(Optional.of(
                UserLogin.builder().id(3003L).role(role).build()));
        var request=new AssignDocumentNumberRequest();request.setBookId(1L);request.setDocumentId(5001L);
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> documentBookService.assignNumber(request)).getErrorCode());
        verifyNoInteractions(documentBookRepository,documentRepository,pdfSigner,documentPdfStorageService,documentWorkflowLogRepository);
    }

    @Test
    void simulatedDocumentCannotConsumeARegistryNumber() {
        Document document = Document.builder().id(5001L).status(DocumentStatus.APPROVED).testDocument(true).build();
        when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
        when(documentRepository.findById(5001L)).thenReturn(Optional.of(document));
        AssignDocumentNumberRequest request = new AssignDocumentNumberRequest();
        request.setBookId(1L); request.setDocumentId(5001L);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentBookService.assignNumber(request)).getErrorCode());
        assertEquals(0, book.getCurrentNumber());
        assertNull(document.getOfficialNumber());
        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        verify(documentBookRepository, never()).save(any());
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(pdfSigner, documentPdfStorageService, documentWorkflowLogRepository);
    }

	@ParameterizedTest
	@EnumSource(value = ErrorCode.class, names = {
			"PKI_CA_NOT_INITIALIZED", "PKI_CA_EXPIRED", "PKI_CA_RECOVERY_REQUIRED",
			"PKI_STORAGE_ERROR", "PKI_USER_KEYSTORE_INVALID"
	})
	void assignNumber_keepsPkiErrorAndDoesNotStoreSignedDocument(ErrorCode code) {
		Document document = Document.builder().id(5001L).status(DocumentStatus.APPROVED)
				.documentTypeCd("QD").primaryFilePath("drafts/sample.pdf").issuingOrg(orgNode).build();
		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(document));
		when(documentPdfStorageService.readPdf("drafts/sample.pdf")).thenReturn(new byte[]{1, 2, 3});
		BusinessException failure = new BusinessException(code);
		when(pdfSigner.signBytes(any(byte[].class), eq("3003"), anyString(), anyString())).thenThrow(failure);
		AssignDocumentNumberRequest request = new AssignDocumentNumberRequest();
		request.setBookId(1L);
		request.setDocumentId(5001L);
		request.setEffectiveDate(LocalDate.of(2026, 9, 10));

		BusinessException actual = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(request));

		assertSame(failure, actual);
		assertEquals(DocumentStatus.APPROVED, document.getStatus());
		assertEquals("drafts/sample.pdf", document.getPrimaryFilePath());
		verify(fileStorageService, never()).storeFile(any(), any(), any());
		verify(documentWorkflowLogRepository, never()).save(any());
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
		req.setNumberPattern("{NUM}/QĐ-ĐHEPU");

		when(documentBookRepository.existsByBookCodeAndDeleteFlgFalse("SVD-QD-DHEPU-2026")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.create(req));
		assertEquals(ErrorCode.DOCUMENT_BOOK_CODE_EXISTS, ex.getErrorCode());
	}

	@Test
	void create_DuplicateYearOrgType_ThrowsException() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-QD-DHEPU-2026-NEW");
		req.setNumberPattern("{NUM}/QĐ-ĐHEPU");
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
		req.setNumberPattern("{NUM}/QĐ-ĐHEPU");
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
	void create_MissingNumPlaceholder_ThrowsException() {
		DocumentBookCreateRequest req = new DocumentBookCreateRequest();
		req.setBookCode("SVD-TEST-2026");
		req.setBookName("Sổ Quyết định 2026");
		req.setYear(2026);
		req.setBookLevel(1);
		req.setDocumentTypeId(3001L);
		req.setOrgNodeId(1001L);
		req.setNumberPattern("123/QĐ-ĐHEPU"); // Không chứa {NUM}

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.create(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		assertTrue(ex.getMessage().contains("{NUM}"));
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void update_Success() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));

		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setBookName("Tên sổ mới");
		req.setNumberPattern("{NUM}/QĐ-BGH");

		DocumentBookResponse res = documentBookService.update(1L, req);

		assertNotNull(res);
		assertEquals("Tên sổ mới", res.getBookName());
		assertEquals("{NUM}/QĐ-BGH", res.getNumberPattern());
	}

	@Test
	void update_NullNumberPattern_PreservesExistingPattern() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));

		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setBookName("Tên sổ mới không gửi numberPattern");
		req.setNumberPattern(null);

		DocumentBookResponse res = documentBookService.update(1L, req);

		assertNotNull(res);
		assertEquals("Tên sổ mới không gửi numberPattern", res.getBookName());
		assertEquals("{NUM}/QĐ-ĐHEPU", res.getNumberPattern());
	}

	@Test
	void update_MissingNumPlaceholder_ThrowsException() {
		book.setIsClosed(false);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));

		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setNumberPattern("123/QĐ-BGH"); // Không chứa {NUM}

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.update(1L, req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		assertTrue(ex.getMessage().contains("{NUM}"));
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void update_ClosedBook_ThrowsException() {
		book.setIsClosed(true);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));

		DocumentBookUpdateRequest req = new DocumentBookUpdateRequest();
		req.setBookName("Tên sổ mới");

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
	void reopen_Success() {
		book.setIsClosed(true);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		documentBookService.reopen(1L);
		assertFalse(book.getIsClosed());
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
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(orgNode)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));
		when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

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
		assertEquals("1/QĐ-ĐHEPU", doc.getOfficialNumber());
		verify(documentBookRepository).save(any(DocumentBook.class));
		verify(documentRepository).saveAndFlush(doc);
	}

	@Test
	void assignNumber_AlreadyHasOfficialNumber_ThrowsException() {
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.officialNumber("99/QĐ-ĐHEPU")
				.documentTypeCd("QD")
				.issuingOrgNode(orgNode)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		assertTrue(ex.getMessage().contains("Văn bản đã được cấp số hiệu"));
		verify(documentBookRepository, never()).save(any(DocumentBook.class));
	}

	@Test
	void assignNumber_DocumentTypeMismatch_ThrowsException() {
		book.setDocumentType(docType);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("TB") // Mismatch: book is QD
				.issuingOrgNode(orgNode)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_OrgNodeMismatch_ThrowsException() {
		book.setOrgNode(orgNode);
		OrgNode differentOrg = OrgNode.builder().id(9999L).code("OTHER").build();
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(differentOrg) // Mismatch: book org is 1001
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_YearMismatch_ThrowsException() {
		book.setYear(2026);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(orgNode)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2025, 9, 10)); // Mismatch: book year is 2026
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_NullDocumentType_ThrowsException() {
		book.setDocumentType(docType);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd(null)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_NullIssuingOrg_ThrowsException() {
		book.setOrgNode(orgNode);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(null)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_NullRequestEffectiveDate_FallsBackToExistingDocumentEffectiveDate_Success() {
		book.setYear(2026);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(orgNode)
				.effectiveDate(LocalDate.of(2026, 5, 1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));
		when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(null);
		req.setDocumentId(5001L);

		AssignDocumentNumberResponse res = documentBookService.assignNumber(req);
		assertNotNull(res);
		assertEquals("1/QĐ-ĐHEPU", res.getDocumentNumber());
		verify(documentBookRepository).save(any(DocumentBook.class));
		verify(documentRepository).saveAndFlush(any(Document.class));
	}

	@Test
	void assignNumber_NullRequestEffectiveDate_ExistingDocumentDateMismatch_ThrowsException() {
		book.setYear(2026);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(orgNode)
				.effectiveDate(LocalDate.of(2025, 5, 1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(null);
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_NullRequestEffectiveDate_AndNullDocumentEffectiveDate_ThrowsException() {
		book.setYear(2026);
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.issuingOrgNode(orgNode)
				.effectiveDate(null)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(null);
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
		verify(documentRepository, never()).saveAndFlush(any());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void assignNumber_DocumentNotFound_ThrowsException() {
		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.empty());

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void assignNumber_DocumentStatusNotApproved_ThrowsException() {
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.DRAFT)
				.build();

		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setDocumentId(5001L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.INVALID_DOCUMENT_STATUS, ex.getErrorCode());
	}

	@Test
	void assignNumber_NullDocumentId_Success() {
		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentBookRepository.save(any(DocumentBook.class))).thenAnswer(inv -> inv.getArgument(0));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));
		req.setDocumentId(null);

		AssignDocumentNumberResponse res = documentBookService.assignNumber(req);

		assertNotNull(res);
		assertEquals("1/QĐ-ĐHEPU", res.getDocumentNumber());
		assertNull(res.getDocumentId());
		verify(documentBookRepository).save(any(DocumentBook.class));
		verifyNoInteractions(documentRepository);
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

		when(documentBookRepository.findAll(ArgumentMatchers.<Specification<DocumentBook>>any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(book)));

		var page = documentBookService.getAll(req);
		assertEquals(1, page.getTotalElements());
		assertEquals("SVD-QD-DHEPU-2026", page.getContent().get(0).getBookCode());
	}

	@Test
	void getAssignedDocuments_Success() {
		Document doc = Document.builder()
				.id(5001L)
				.title("Quyết định ban hành")
				.officialNumber("1/QĐ-ĐHEPU")
				.status(DocumentStatus.APPROVED)
				.documentBook(book)
				.documentTypeCd("QD")
				.build();

		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(eq(1L), any(), any(), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(doc)));
		when(dictionaryItemRepository.findByGroupCdAndItemCdInAndDeleteFlgFalse(eq("DOCUMENT_TYPE"), any(Collection.class)))
				.thenReturn(List.of(docType));

		var result = documentBookService.getAssignedDocuments(1L, null, null, PageRequest.of(0, 10));
		assertNotNull(result);
		assertEquals(1, result.getTotalElements());
		assertEquals("1/QĐ-ĐHEPU", result.getContent().get(0).getOfficialNumber());
		assertEquals(1L, result.getContent().get(0).getDocumentBookId());
		assertEquals("Quyết định", result.getContent().get(0).getDocumentTypeName());
		verify(dictionaryItemRepository, times(1))
				.findByGroupCdAndItemCdInAndDeleteFlgFalse(eq("DOCUMENT_TYPE"), any(Collection.class));
	}

	@Test
	void getAssignedDocuments_WithWildcardKeyword_EscapesProperly() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(eq(1L), any(), any(), any(), eq("100\\%\\_special\\\\char"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		var result = documentBookService.getAssignedDocuments(1L, "  100%_special\\char  ", null, PageRequest.of(0, 10));
		assertNotNull(result);
		verify(documentRepository).findAssignedDocuments(eq(1L), any(), any(), any(), eq("100\\%\\_special\\\\char"), any(Pageable.class));
	}

	private static DrafterName drafterName(Long id, String username, String fullName) {
		return new DrafterName() {
			public Long getUserId() { return id; }
			public String getUsername() { return username; }
			public String getFullName() { return fullName; }
		};
	}

	@Test
	void getAssignedDocuments_batchesPageAuthorsAndPreservesFieldsFiltersAndPagination() {
		UserLogin first = mock(UserLogin.class);
		UserLogin second = mock(UserLogin.class);
		when(first.getId()).thenReturn(11L);
		when(second.getId()).thenReturn(22L);
		Instant date = Instant.parse("2026-10-27T18:00:00Z");
		List<Document> docs = List.of(first, second, first).stream().map(author -> {
			Document doc = Document.builder().id(author == first ? 50L : 40L).drafter(author)
					.draftCode("draft").title("Title").summary("Summary").content("Content")
					.documentTypeCd("QD").issuingOrgNode(orgNode).status(DocumentStatus.APPROVED)
					.currentVersion(3).securityLevel(DocumentSecurityLevel.NORMAL).urgencyLevel(DocumentUrgencyLevel.NORMAL)
					.submittedAt(date).officialNumber("01/QD").issueDate(date).effectiveDate(date)
					.officialFilePath("official.pdf").officialHash("hash").build();
			doc.setCreatedDate(date);
			doc.setUpdatedDate(date);
			return doc;
		}).toList();
		docs.get(2).setId(30L);
		Pageable pageable = PageRequest.of(1, 3);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(1L, "QD", 1001L, DocumentStatus.APPROVED,
				"100\\%\\_x\\\\y", pageable)).thenReturn(new PageImpl<>(docs, pageable, 10));
		when(dictionaryItemRepository.findByGroupCdAndItemCdInAndDeleteFlgFalse("DOCUMENT_TYPE", Set.of("QD")))
				.thenReturn(List.of(docType));
		when(userInfoRepository.findDrafterNamesByUserIds(Set.of(11L, 22L)))
				.thenReturn(List.of(drafterName(11L, "alice", "Alice"), drafterName(22L, "bob", null)));

		var page = documentBookService.getAssignedDocuments(1L, " 100%_x\\y ", DocumentStatus.APPROVED, pageable);
		assertEquals(List.of(50L, 40L, 30L), page.stream().map(DocumentResponse::getId).toList());
		assertEquals(pageable, page.getPageable());
		assertEquals(1, page.getNumber());
		assertEquals(3, page.getSize());
		assertEquals(10, page.getTotalElements());
		assertEquals(4, page.getTotalPages());
		for (int i = 0; i < docs.size(); i++) {
			boolean alice = i != 1;
			DocumentResponse expected = DocumentResponse.builder().id(docs.get(i).getId()).draftCode("draft")
					.title("Title").summary("Summary").content("Content").documentTypeCd("QD").documentTypeName("Quyết định")
					.issuingOrgNodeId(1001L).issuingOrgNodeName(orgNode.getNameVn())
					.drafterId(alice ? 11L : 22L).drafterUsername(alice ? "alice" : "bob").drafterFullName(alice ? "Alice" : "bob")
					.status(DocumentStatus.APPROVED).statusDescription(DocumentStatus.APPROVED.getDescription()).currentVersion(3)
					.securityLevel(DocumentSecurityLevel.NORMAL).urgencyLevel(DocumentUrgencyLevel.NORMAL)
					.submittedAt(date).createdDate(date).updatedDate(date).officialNumber("01/QD")
					.documentBookId(1L).documentBookCode(book.getBookCode())
					.issueDate(LocalDate.of(2026, 10, 28)).effectiveDate(LocalDate.of(2026, 10, 28))
					.officialFilePath("official.pdf").officialHash("hash").build();
			assertEquals(expected, page.getContent().get(i));
		}
		verify(first, never()).getUsername();
		verify(second, never()).getUsername();
		verify(userInfoRepository).findDrafterNamesByUserIds(Set.of(11L, 22L));
		verifyNoMoreInteractions(userInfoRepository);
		verify(documentRepository).findAssignedDocuments(1L, "QD", 1001L, DocumentStatus.APPROVED, "100\\%\\_x\\\\y", pageable);
		verifyNoMoreInteractions(documentRepository);
	}

	@Test
	void getAssignedDocuments_emptyPageDoesNotLookupAuthors() {
		Pageable pageable = PageRequest.of(3, 10);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(1L, "QD", 1001L, null, null, pageable))
				.thenReturn(new PageImpl<>(List.of(), pageable, 25));
		var page = documentBookService.getAssignedDocuments(1L, "  ", null, pageable);
		assertTrue(page.isEmpty());
		assertEquals(pageable, page.getPageable());
		assertEquals(25, page.getTotalElements());
		verifyNoInteractions(userInfoRepository, dictionaryItemRepository);
	}

	@Test
	void getAssignedDocuments_missingAuthorOrIdSkipsBatchAndKeepsDefaults() {
		Document missing = Document.builder().id(10L).build();
		Document noId = Document.builder().id(20L).drafter(UserLogin.builder().username("legacy").build()).build();
		Pageable pageable = PageRequest.of(0, 10);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(1L, "QD", 1001L, null, null, pageable))
				.thenReturn(new PageImpl<>(List.of(missing, noId)));
		var result = documentBookService.getAssignedDocuments(1L, null, null, pageable).getContent();
		assertNull(result.get(0).getDrafterId());
		assertNull(result.get(0).getDrafterUsername());
		assertNull(result.get(0).getDrafterFullName());
		assertNull(result.get(1).getDrafterId());
		assertEquals("legacy", result.get(1).getDrafterUsername());
		assertEquals("legacy", result.get(1).getDrafterFullName());
		assertEquals(DocumentStatus.DRAFT, result.getFirst().getStatus());
		assertEquals(DocumentSecurityLevel.NORMAL, result.getFirst().getSecurityLevel());
		assertEquals(DocumentUrgencyLevel.NORMAL, result.getFirst().getUrgencyLevel());
		verifyNoInteractions(userInfoRepository);
	}

	@Test
	void getAssignedDocuments_preservesEmptyNameAndEntityFallbackForMissingProjection() {
		Pageable pageable = PageRequest.of(0, 10);
		List<Document> docs = List.of(
				Document.builder().id(10L).drafter(UserLogin.builder().id(11L).build()).build(),
				Document.builder().id(20L).drafter(UserLogin.builder().id(22L).username("legacy").build()).build());
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(1L, "QD", 1001L, null, null, pageable))
				.thenReturn(new PageImpl<>(docs));
		when(userInfoRepository.findDrafterNamesByUserIds(Set.of(11L, 22L)))
				.thenReturn(List.of(drafterName(11L, "empty", "")));
		var result = documentBookService.getAssignedDocuments(1L, null, null, pageable).getContent();
		assertEquals("", result.get(0).getDrafterFullName());
		assertEquals("empty", result.get(0).getDrafterUsername());
		assertEquals("legacy", result.get(1).getDrafterFullName());
		assertEquals("legacy", result.get(1).getDrafterUsername());
		verify(userInfoRepository).findDrafterNamesByUserIds(Set.of(11L, 22L));
		verifyNoMoreInteractions(userInfoRepository);
	}

	@Test
	void getAssignedDocuments_batchFailurePropagates() {
		Pageable pageable = PageRequest.of(0, 10);
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findAssignedDocuments(1L, "QD", 1001L, null, null, pageable))
				.thenReturn(new PageImpl<>(List.of(Document.builder().drafter(UserLogin.builder().id(11L).build()).build())));
		RuntimeException failure = new IllegalStateException("lookup failed");
		when(userInfoRepository.findDrafterNamesByUserIds(Set.of(11L))).thenThrow(failure);
		assertSame(failure, assertThrows(IllegalStateException.class,
				() -> documentBookService.getAssignedDocuments(1L, null, null, pageable)));
		verify(userInfoRepository).findDrafterNamesByUserIds(Set.of(11L));
		verifyNoMoreInteractions(userInfoRepository);
	}

	@Test
	void getAssignedDocuments_missingBookFailsBeforeDocumentOrAuthorLookup() {
		when(documentBookRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.empty());
		var failure = assertThrows(BusinessException.class,
				() -> documentBookService.getAssignedDocuments(1L, null, null, PageRequest.of(0, 10)));
		assertEquals(ErrorCode.DOCUMENT_BOOK_NOT_FOUND, failure.getErrorCode());
		verifyNoInteractions(documentRepository, userInfoRepository, dictionaryItemRepository);
	}

	@Test
	void assignNumber_WithPdfAndClerk_SignsPdfSuccessfully() {
		UserLogin clerk = UserLogin.builder()
				.id(3003L)
				.username("vanthu")
				.role(Role.CLERK)
				.build();
		Document doc = Document.builder()
				.id(5001L)
				.status(DocumentStatus.APPROVED)
				.documentTypeCd("QD")
				.primaryFilePath("uploads/sample.pdf")
				.issuingOrg(orgNode)
				.build();

		Authentication auth = mock(Authentication.class);
		when(auth.isAuthenticated()).thenReturn(true);
		when(auth.getPrincipal()).thenReturn(new JwtUserPrincipal(3003L, "vanthu", "CLERK", true, List.of()));
		SecurityContextHolder.getContext().setAuthentication(auth);

		when(userLoginRepository.findByIdAndDeleteFlgFalse(3003L)).thenReturn(Optional.of(clerk));
		when(documentBookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(book));
		when(documentRepository.findById(5001L)).thenReturn(Optional.of(doc));
		when(documentPdfStorageService.readPdf("uploads/sample.pdf")).thenReturn(new byte[]{1, 2, 3});
		PdfSigner.SignResult signResult = new PdfSigner.SignResult(
				"3003", "Cán bộ Văn thư", "serial123", "hash_clerk_sha256",
				new java.util.Date(), "sig_clerk", java.nio.file.Path.of("drafts/test.pdf"), new byte[]{4, 5, 6}
		);
		when(pdfSigner.signBytes(any(byte[].class), eq("3003"), anyString(), anyString())).thenReturn(signResult);
		when(documentPdfStorageService.storeSignedPdf(any(byte[].class), eq("initial_signed_5001.pdf"), eq(5001L)))
				.thenReturn("storage://35001");

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);
		req.setDocumentId(5001L);
		req.setEffectiveDate(LocalDate.of(2026, 9, 10));

		try {
			AssignDocumentNumberResponse res = documentBookService.assignNumber(req);
			assertNotNull(res);
			assertEquals("1/QĐ-ĐHEPU", res.getDocumentNumber());
			assertEquals("hash_clerk_sha256", res.getSignatureHash());
			assertEquals("storage://35001", res.getSignedPdfPath());
			assertEquals("storage://35001", doc.getPrimaryFilePath());
			verify(pdfSigner).signBytes(any(byte[].class), eq("3003"), anyString(), contains("Văn thư cấp số văn bản"));
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void assignNumber_AsLeader_ThrowsForbidden() {
		UserLogin leader = UserLogin.builder()
				.id(3001L)
				.username("leader")
				.role(Role.LEADER)
				.build();

		Authentication auth = mock(Authentication.class);
		when(auth.isAuthenticated()).thenReturn(true);
		when(auth.getPrincipal()).thenReturn(new JwtUserPrincipal(3001L, "leader", "LEADER", true, List.of()));
		SecurityContextHolder.getContext().setAuthentication(auth);

		when(userLoginRepository.findByIdAndDeleteFlgFalse(3001L)).thenReturn(Optional.of(leader));

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);

		try {
			BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
			assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void assignNumber_WhenNoAuthentication_ThrowsUnauthorized() {
		SecurityContextHolder.clearContext();

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
	}

	@Test
	void assignNumber_WhenAnonymousAuthentication_ThrowsUnauthorized() {
		Authentication anonymous = new AnonymousAuthenticationToken(
				"key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
		SecurityContextHolder.getContext().setAuthentication(anonymous);

		AssignDocumentNumberRequest req = new AssignDocumentNumberRequest();
		req.setBookId(1L);

		BusinessException ex = assertThrows(BusinessException.class, () -> documentBookService.assignNumber(req));
		assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
	}
}

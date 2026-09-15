package vn.edu.epu.eoffice_be.service.cron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBookYearResetJobTest {

	@Mock
	DocumentBookRepository documentBookRepository;

	@Mock
	TransactionTemplate transactionTemplate;

	@Mock
	TransactionStatus transactionStatus;

	Clock clock;

	DocumentBookYearResetJob documentBookYearResetJob;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

		lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(transactionStatus);
		});

		lenient().doAnswer(invocation -> {
			Consumer<TransactionStatus> action = invocation.getArgument(0);
			action.accept(transactionStatus);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());

		documentBookYearResetJob = new DocumentBookYearResetJob(documentBookRepository, transactionTemplate, clock);
	}

	@Test
	void resetAllBookCounters_DerivesYearUsingVietnamTimezone() {
		// 2024-12-31T17:30:00Z is 2025-01-01T00:30:00+07:00 in Vietnam (+07:00), but 2024 in UTC.
		Clock nonVietnamClock = Clock.fixed(Instant.parse("2024-12-31T17:30:00Z"), ZoneId.of("UTC"));
		DocumentBookYearResetJob jobWithNonVietnamClock = new DocumentBookYearResetJob(documentBookRepository, transactionTemplate, nonVietnamClock);

		DictionaryItem docType = DictionaryItem.builder().id(3001L).itemCd("QD").build();
		OrgNode orgNode = OrgNode.builder().id(1001L).build();

		DocumentBook oldBook = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-2024")
				.bookName("Sổ 2024")
				.year(2024)
				.documentType(docType)
				.orgNode(orgNode)
				.isClosed(false)
				.build();

		when(documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse()).thenReturn(List.of(oldBook));
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(2025, 1001L, 3001L)).thenReturn(false);

		jobWithNonVietnamClock.resetAllBookCounters();

		verify(documentBookRepository).existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(2025, 1001L, 3001L);
		verify(documentBookRepository).save(argThat(b -> b != null && Integer.valueOf(2025).equals(b.getYear())
				&& Long.valueOf(1001L).equals(b.getOrgNode().getId()) && Long.valueOf(3001L).equals(b.getDocumentType().getId())));
		assertTrue(oldBook.getIsClosed());
	}

	@Test
	void resetAllBookCounters_ClosesOldBooksAndCreatesNewBooks() {
		int currentYear = 2026;
		int oldYear = 2025;

		DictionaryItem docType = DictionaryItem.builder()
				.id(3001L)
				.itemCd("QD")
				.nameVn("Quyết định")
				.build();

		OrgNode orgNode = OrgNode.builder()
				.id(1001L)
				.code("DHEPU")
				.nameVn("Trường Đại học Điện Lực")
				.build();

		DocumentBook oldBook = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-" + oldYear)
				.bookName("Sổ Quyết định cấp Trường năm " + oldYear)
				.year(oldYear)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(orgNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.currentNumber(125)
				.isClosed(false)
				.deleteFlg(false)
				.build();

		when(documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse()).thenReturn(List.of(oldBook));
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
				currentYear, 1001L, 3001L)).thenReturn(false);

		documentBookYearResetJob.resetAllBookCounters();

		verify(transactionTemplate, atLeastOnce()).execute(any());

		// Old book must be closed
		assertTrue(oldBook.getIsClosed());

		// Verify save was called for new book and old book without positional index assumption
		ArgumentCaptor<DocumentBook> captor = ArgumentCaptor.forClass(DocumentBook.class);
		verify(documentBookRepository, times(2)).save(captor.capture());

		List<DocumentBook> savedBooks = captor.getAllValues();
		DocumentBook newBook = savedBooks.stream()
				.filter(b -> Integer.valueOf(currentYear).equals(b.getYear()))
				.findFirst()
				.orElseThrow();
		DocumentBook closedOldBook = savedBooks.stream()
				.filter(b -> oldBook.getId().equals(b.getId()))
				.findFirst()
				.orElseThrow();

		assertEquals(currentYear, newBook.getYear());
		assertEquals("SVD-QD-DHEPU-" + currentYear, newBook.getBookCode());
		assertEquals("Sổ Quyết định cấp Trường năm " + currentYear, newBook.getBookName());
		assertEquals(0, newBook.getCurrentNumber());
		assertFalse(newBook.getIsClosed());
		assertEquals(1, newBook.getBookLevel());
		assertEquals("{NUM}/QĐ-ĐHEPU", newBook.getNumberPattern());

		assertEquals(oldBook.getId(), closedOldBook.getId());
		assertTrue(closedOldBook.getIsClosed());
	}

	@Test
	void resetAllBookCounters_SkipsCurrentYearAndFutureYearActiveBooks() {
		int currentYear = 2026;

		DocumentBook currentYearBook = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-" + currentYear)
				.year(currentYear)
				.isClosed(false)
				.build();

		DocumentBook futureYearBook = DocumentBook.builder()
				.id(2L)
				.bookCode("SVD-QD-DHEPU-" + (currentYear + 1))
				.year(currentYear + 1)
				.isClosed(false)
				.build();

		when(documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse())
				.thenReturn(List.of(currentYearBook, futureYearBook));

		documentBookYearResetJob.resetAllBookCounters();

		// Neither current nor future year books should be closed
		assertFalse(currentYearBook.getIsClosed());
		assertFalse(futureYearBook.getIsClosed());
		verify(documentBookRepository, never()).save(any());
	}

	@Test
	void resetAllBookCounters_HandlesDataIntegrityViolationExceptionGracefully() {
		int currentYear = 2026;
		int oldYear = 2025;

		DictionaryItem docType = DictionaryItem.builder()
				.id(3001L)
				.itemCd("QD")
				.build();

		OrgNode orgNode = OrgNode.builder()
				.id(1001L)
				.code("DHEPU")
				.build();

		DocumentBook oldBook = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-" + oldYear)
				.bookName("Sổ " + oldYear)
				.year(oldYear)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(orgNode)
				.numberPattern("{NUM}/QĐ-ĐHEPU")
				.isClosed(false)
				.build();

		when(documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse()).thenReturn(List.of(oldBook));
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
				currentYear, 1001L, 3001L)).thenReturn(false);

		// Creating new book throws DataIntegrityViolationException
		when(documentBookRepository.save(argThat(b -> b != null && Integer.valueOf(currentYear).equals(b.getYear()))))
				.thenThrow(new DataIntegrityViolationException("Duplicate key"));

		// Must not throw exception
		assertDoesNotThrow(() -> documentBookYearResetJob.resetAllBookCounters());

		// Old book must NOT be saved as closed because creation failed and new book still does not exist
		verify(documentBookRepository, never()).save(argThat(b -> b != null && oldBook.getId().equals(b.getId())));
		assertFalse(oldBook.getIsClosed());
	}

	@Test
	void resetAllBookCounters_DataIntegrityViolationOnSave_ClosesOldBookIfNewBookExists() {
		int currentYear = 2026;
		int oldYear = 2025;

		DictionaryItem docType = DictionaryItem.builder().id(3001L).itemCd("QD").build();
		OrgNode orgNode = OrgNode.builder().id(1001L).code("DHEPU").build();

		DocumentBook oldBook = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-" + oldYear)
				.bookName("Sổ " + oldYear)
				.year(oldYear)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(orgNode)
				.isClosed(false)
				.build();

		when(documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse()).thenReturn(List.of(oldBook));

		// First check in transaction returns false (so it attempts to create new book)
		// Second check in catch block returns true (simulating concurrent creation succeeded)
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(currentYear, 1001L, 3001L))
				.thenReturn(false)
				.thenReturn(true);

		when(documentBookRepository.save(argThat(b -> b != null && Integer.valueOf(currentYear).equals(b.getYear()))))
				.thenThrow(new DataIntegrityViolationException("Duplicate key race condition"));

		when(documentBookRepository.findByIdAndDeleteFlgFalse(oldBook.getId())).thenReturn(Optional.of(oldBook));

		assertDoesNotThrow(() -> documentBookYearResetJob.resetAllBookCounters());

		verify(documentBookRepository, times(2)).existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(currentYear, 1001L, 3001L);

		InOrder inOrder = inOrder(transactionTemplate, documentBookRepository);
		inOrder.verify(transactionTemplate).execute(any());
		inOrder.verify(documentBookRepository).save(argThat(b -> b != null && Integer.valueOf(currentYear).equals(b.getYear())));
		inOrder.verify(transactionTemplate).execute(any());
		inOrder.verify(documentBookRepository).save(argThat(b -> b != null && oldBook.getId().equals(b.getId())));

		assertTrue(oldBook.getIsClosed());
	}

	@Test
	void resetAllBookCounters_DataIntegrityViolationOnSave_OldBookNotFoundInRecovery_ClosedCountNotIncremented() {
		int currentYear = 2026;
		int oldYear = 2025;

		DictionaryItem docType = DictionaryItem.builder().id(3001L).itemCd("QD").build();
		OrgNode orgNode = OrgNode.builder().id(1001L).code("DHEPU").build();

		DocumentBook oldBook = DocumentBook.builder()
				.id(1L)
				.bookCode("SVD-QD-DHEPU-" + oldYear)
				.bookName("Sổ " + oldYear)
				.year(oldYear)
				.bookLevel(1)
				.documentType(docType)
				.orgNode(orgNode)
				.isClosed(false)
				.build();

		when(documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse()).thenReturn(List.of(oldBook));
		when(documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(currentYear, 1001L, 3001L))
				.thenReturn(false)
				.thenReturn(true);

		when(documentBookRepository.save(argThat(b -> b != null && Integer.valueOf(currentYear).equals(b.getYear()))))
				.thenThrow(new DataIntegrityViolationException("Duplicate key race condition"));

		when(documentBookRepository.findByIdAndDeleteFlgFalse(oldBook.getId())).thenReturn(Optional.empty());

		assertDoesNotThrow(() -> documentBookYearResetJob.resetAllBookCounters());

		verify(documentBookRepository, never()).save(argThat(b -> b != null && oldBook.getId().equals(b.getId())));
		assertFalse(oldBook.getIsClosed());
	}
}


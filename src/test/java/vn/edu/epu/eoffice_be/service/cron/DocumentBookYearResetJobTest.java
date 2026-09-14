package vn.edu.epu.eoffice_be.service.cron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBookYearResetJobTest {

	@Mock
	DocumentBookRepository documentBookRepository;

	@InjectMocks
	DocumentBookYearResetJob documentBookYearResetJob;

	@Test
	void resetAllBookCounters_ClosesOldBooksAndCreatesNewBooks() {
		int currentYear = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
		int oldYear = currentYear - 1;

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

		// Old book must be closed
		assertTrue(oldBook.getIsClosed());

		// Verify save was called for old book and new book
		ArgumentCaptor<DocumentBook> captor = ArgumentCaptor.forClass(DocumentBook.class);
		verify(documentBookRepository, times(2)).save(captor.capture());

		List<DocumentBook> savedBooks = captor.getAllValues();
		DocumentBook newBook = savedBooks.get(1);

		assertEquals(currentYear, newBook.getYear());
		assertEquals("SVD-QD-DHEPU-" + currentYear, newBook.getBookCode());
		assertEquals("Sổ Quyết định cấp Trường năm " + currentYear, newBook.getBookName());
		assertEquals(0, newBook.getCurrentNumber());
		assertFalse(newBook.getIsClosed());
		assertEquals(1, newBook.getBookLevel());
		assertEquals("{NUM}/QĐ-ĐHEPU", newBook.getNumberPattern());
	}

	@Test
	void resetAllBookCounters_SkipsCurrentYearAndFutureYearActiveBooks() {
		int currentYear = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();

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
		int currentYear = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
		int oldYear = currentYear - 1;

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

		// First save is closing old book (succeeds), second save is inserting new book (throws DataIntegrityViolationException)
		when(documentBookRepository.save(oldBook)).thenReturn(oldBook);
		when(documentBookRepository.save(argThat(b -> b != null && Integer.valueOf(currentYear).equals(b.getYear()))))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));

		// Must not throw exception
		assertDoesNotThrow(() -> documentBookYearResetJob.resetAllBookCounters());

		assertTrue(oldBook.getIsClosed());
	}
}

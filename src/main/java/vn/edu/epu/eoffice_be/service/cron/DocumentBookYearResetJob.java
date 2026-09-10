package vn.edu.epu.eoffice_be.service.cron;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentBookYearResetJob {

	DocumentBookRepository documentBookRepository;

	// Chạy vào 00:00:00 ngày 01 tháng 01 hàng năm
	@Scheduled(cron = "${eoffice.cron.book-reset:0 0 0 1 1 *}")
	@Transactional
	public void resetAllBookCounters() {
		int newYear = LocalDate.now().getYear();
		int previousYear = newYear - 1;
		log.info("[CRON] Bắt đầu tự động chuyển giao và reset sổ văn bản sang năm mới: {}", newYear);

		List<DocumentBook> activeBooks = documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse();
		int closedCount = 0;
		int createdCount = 0;

		for (DocumentBook oldBook : activeBooks) {
			// Đóng sổ năm cũ (bảo toàn lịch sử, không xóa)
			oldBook.setIsClosed(true);
			documentBookRepository.save(oldBook);
			closedCount++;

			// Kiểm tra tránh tạo trùng nếu sổ cho năm mới đã được tạo trước đó
			boolean exists = documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
					newYear, oldBook.getOrgNode().getId(), oldBook.getDocumentType().getId());
			if (exists) {
				continue;
			}

			// Tạo mã sổ và tên sổ mới tương ứng năm mới
			String newBookCode = oldBook.getBookCode().contains(String.valueOf(previousYear))
					? oldBook.getBookCode().replace(String.valueOf(previousYear), String.valueOf(newYear))
					: oldBook.getBookCode() + "-" + newYear;

			String newBookName = oldBook.getBookName().contains(String.valueOf(previousYear))
					? oldBook.getBookName().replace(String.valueOf(previousYear), String.valueOf(newYear))
					: oldBook.getBookName() + " năm " + newYear;

			DocumentBook newBook = DocumentBook.builder()
					.bookCode(newBookCode)
					.bookName(newBookName)
					.year(newYear)
					.bookLevel(oldBook.getBookLevel())
					.documentType(oldBook.getDocumentType())
					.orgNode(oldBook.getOrgNode())
					.numberPattern(oldBook.getNumberPattern())
					.currentNumber(0)
					.isClosed(false)
					.deleteFlg(false)
					.build();

			documentBookRepository.save(newBook);
			createdCount++;
		}

		log.info("[CRON] Hoàn tất: Đã đóng {} sổ năm cũ và tạo {} sổ mới cho năm {}", closedCount, createdCount, newYear);
	}
}

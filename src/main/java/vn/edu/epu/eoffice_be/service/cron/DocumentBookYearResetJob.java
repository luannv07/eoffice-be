package vn.edu.epu.eoffice_be.service.cron;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentBookYearResetJob {

	private static final String ZONE_VIETNAM = "Asia/Ho_Chi_Minh";

	DocumentBookRepository documentBookRepository;
	TransactionTemplate transactionTemplate;
	Clock clock;

	// Chạy vào 00:00:00 ngày 01 tháng 01 hàng năm (giờ Việt Nam)
	@Scheduled(cron = "${eoffice.cron.book-reset:0 0 0 1 1 *}", zone = ZONE_VIETNAM)
	public void resetAllBookCounters() {
		int newYear = LocalDate.now(clock.withZone(java.time.ZoneId.of(ZONE_VIETNAM))).getYear();
		int previousYear = newYear - 1;
		log.info("[CRON] Bắt đầu tự động chuyển giao và reset sổ văn bản sang năm mới: {}", newYear);

		List<DocumentBook> activeBooks = documentBookRepository.findAllByDeleteFlgFalseAndIsClosedFalse();
		int closedCount = 0;
		int createdCount = 0;

		for (DocumentBook oldBook : activeBooks) {
			// Chỉ xử lý các sổ của năm cũ cần chuyển giao (bỏ qua nếu year null, year != previousYear hoặc year >= newYear)
			if (oldBook.getYear() == null || oldBook.getYear() != previousYear) {
				continue;
			}

			try {
				boolean created = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
					boolean wasCreated = false;

					// Kiểm tra tránh tạo trùng nếu sổ cho năm mới đã được tạo trước đó
					boolean exists = documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
							newYear, oldBook.getOrgNode().getId(), oldBook.getDocumentType().getId());
					if (!exists) {
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
						wasCreated = true;
					}

					// Đóng sổ năm cũ sau khi đã tạo mới thành công (hoặc sổ mới đã tồn tại)
					oldBook.setIsClosed(true);
					documentBookRepository.save(oldBook);

					return wasCreated;
				}));

				closedCount++;
				if (created) {
					createdCount++;
				}
			} catch (DataIntegrityViolationException ex) {
				log.warn("[CRON] Bỏ qua tạo sổ mới cho id={} năm={} do xung đột dữ liệu hoặc đã tồn tại: {}",
						oldBook.getId(), oldBook.getYear(), ex.getMessage(), ex);

				try {
					boolean newBookExists = documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
							newYear, oldBook.getOrgNode().getId(), oldBook.getDocumentType().getId());
					if (newBookExists) {
						transactionTemplate.executeWithoutResult(status -> {
							DocumentBook freshOldBook = documentBookRepository.findByIdAndDeleteFlgFalse(oldBook.getId()).orElse(null);
							if (freshOldBook != null && !Boolean.TRUE.equals(freshOldBook.getIsClosed())) {
								freshOldBook.setIsClosed(true);
								documentBookRepository.save(freshOldBook);
							}
						});
						oldBook.setIsClosed(true);
						closedCount++;
					}
				} catch (Exception closeEx) {
					log.error("[CRON] Lỗi khi cố gắng đóng sổ năm cũ id={} sau DataIntegrityViolationException: {}",
							oldBook.getId(), closeEx.getMessage(), closeEx);
				}
			} catch (Exception ex) {
				log.error("[CRON] Lỗi khi chuyển giao sổ id={} năm={}: {}",
						oldBook.getId(), oldBook.getYear(), ex.getMessage(), ex);
			}
		}

		log.info("[CRON] Hoàn tất: Đã đóng {} sổ năm cũ và tạo {} sổ mới cho năm {}", closedCount, createdCount, newYear);
	}
}

package vn.edu.epu.eoffice_be.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentSecurityLevel;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentUrgencyLevel;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.AssignDocumentNumberRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookCreateRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookFilterRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentBookUpdateRequest;
import vn.edu.epu.eoffice_be.dto.response.AssignDocumentNumberResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentBookResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.service.DocumentBookService;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentBookServiceImpl implements DocumentBookService {

	DocumentBookRepository documentBookRepository;
	DictionaryItemRepository dictionaryItemRepository;
	OrgNodeRepository orgNodeRepository;
	DocumentRepository documentRepository;

	@Override
	@Transactional(readOnly = true)
	public Page<DocumentBookResponse> getAll(DocumentBookFilterRequest request) {
		Specification<DocumentBook> spec = (root, query, cb) -> cb.isFalse(root.get("deleteFlg"));

		if (request.getYear() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("year"), request.getYear()));
		}
		if (request.getBookLevel() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("bookLevel"), request.getBookLevel()));
		}
		if (request.getOrgNodeId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("orgNode").get("id"), request.getOrgNodeId()));
		}
		if (request.getDocumentTypeId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("documentType").get("id"), request.getDocumentTypeId()));
		}
		if (request.getIsClosed() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("isClosed"), request.getIsClosed()));
		}
		if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
			String kw = "%" + request.getKeyword().trim().toLowerCase() + "%";
			spec = spec.and((root, query, cb) -> cb.or(
					cb.like(cb.lower(root.get("bookCode")), kw),
					cb.like(cb.lower(root.get("bookName")), kw)
			));
		}

		Pageable pageable = request.pageable();
		return documentBookRepository.findAll(spec, pageable).map(this::toDto);
	}

	@Override
	@Transactional(readOnly = true)
	public DocumentBookResponse getDetail(Long id) {
		return documentBookRepository.findByIdAndDeleteFlgFalse(id)
				.map(this::toDto)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));
	}

	@Override
	@Transactional
	public DocumentBookResponse create(DocumentBookCreateRequest request) {
		if (request.getNumberPattern() == null || !request.getNumberPattern().contains("{NUM}")) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Mẫu số văn bản phải chứa {NUM}");
		}

		if (documentBookRepository.existsByBookCodeAndDeleteFlgFalse(request.getBookCode().trim())) {
			throw new BusinessException(ErrorCode.DOCUMENT_BOOK_CODE_EXISTS);
		}

		if (documentBookRepository.existsByYearAndOrgNodeIdAndDocumentTypeIdAndDeleteFlgFalse(
				request.getYear(), request.getOrgNodeId(), request.getDocumentTypeId())) {
			throw new BusinessException(ErrorCode.DOCUMENT_BOOK_DUPLICATE);
		}

		DictionaryItem documentType = dictionaryItemRepository.findByIdAndDeleteFlgFalse(request.getDocumentTypeId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy loại văn bản"));

		if (!"DOCUMENT_TYPE".equals(documentType.getGroupCd())) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Mục từ điển không thuộc nhóm loại văn bản (DOCUMENT_TYPE)");
		}

		OrgNode orgNode = orgNodeRepository.findByIdAndDeleteFlgFalse(request.getOrgNodeId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn vị/phòng ban"));

		DocumentBook book = DocumentBook.builder()
				.bookCode(request.getBookCode().trim())
				.bookName(request.getBookName().trim())
				.year(request.getYear())
				.bookLevel(request.getBookLevel())
				.documentType(documentType)
				.orgNode(orgNode)
				.numberPattern(request.getNumberPattern().trim())
				.currentNumber(0)
				.isClosed(false)
				.deleteFlg(false)
				.build();

		return toDto(documentBookRepository.save(book));
	}

	@Override
	@Transactional
	public DocumentBookResponse update(Long id, DocumentBookUpdateRequest request) {
		DocumentBook book = documentBookRepository.findByIdAndDeleteFlgFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));

		if (Boolean.TRUE.equals(book.getIsClosed())) {
			throw new BusinessException(ErrorCode.DOCUMENT_BOOK_CLOSED, "Sổ văn bản đã đóng, không thể chỉnh sửa");
		}

		if (request.getBookName() != null && !request.getBookName().isBlank()) {
			book.setBookName(request.getBookName().trim());
		}
		if (request.getNumberPattern() != null) {
			if (request.getNumberPattern().isBlank() || !request.getNumberPattern().contains("{NUM}")) {
				throw new BusinessException(ErrorCode.BAD_REQUEST, "Mẫu số văn bản phải chứa {NUM}");
			}
			book.setNumberPattern(request.getNumberPattern().trim());
		}

		if (book.getNumberPattern() == null || !book.getNumberPattern().contains("{NUM}")) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Mẫu số văn bản phải chứa {NUM}");
		}

		return toDto(documentBookRepository.save(book));
	}

	@Override
	@Transactional
	public void close(Long id) {
		DocumentBook book = documentBookRepository.findByIdAndDeleteFlgFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));

		book.setIsClosed(true);
		documentBookRepository.save(book);
	}

	@Override
	@Transactional
	public void reopen(Long id) {
		DocumentBook book = documentBookRepository.findByIdAndDeleteFlgFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));

		book.setIsClosed(false);
		documentBookRepository.save(book);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		DocumentBook book = documentBookRepository.findByIdAndDeleteFlgFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));

		book.setDeleteFlg(true);
		documentBookRepository.save(book);
	}

	@Override
	@Transactional
	public AssignDocumentNumberResponse assignNumber(AssignDocumentNumberRequest request) {
		// 1. Pessimistic Write Lock: đảm bảo không trùng số dù nhiều request đồng thời
		DocumentBook book = documentBookRepository.findByIdForUpdate(request.getBookId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));

		if (Boolean.TRUE.equals(book.getIsClosed())) {
			throw new BusinessException(ErrorCode.DOCUMENT_BOOK_CLOSED);
		}

		// 2. Kiểm tra văn bản tồn tại và trạng thái APPROVED (nếu có truyền documentId)
		Document document = null;
		if (request.getDocumentId() != null) {
			document = documentRepository.findById(request.getDocumentId())
					.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

			if (document.getStatus() != DocumentStatus.APPROVED
					&& document.getStatus() != DocumentStatus.LEADER_REVIEW
					&& document.getStatus() != DocumentStatus.PENDING_SIGN) {
				throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATUS, "Văn bản chưa ở trạng thái Đã phê duyệt (APPROVED) hoặc Chờ ký");
			}

			if (document.getOfficialNumber() != null && !document.getOfficialNumber().isBlank()) {
				throw new BusinessException(ErrorCode.BAD_REQUEST,
						"Văn bản đã được cấp số hiệu: " + document.getOfficialNumber());
			}

			// Kiểm tra ràng buộc giữa Document và DocumentBook (loại văn bản, đơn vị, năm hiệu lực)
			if (book.getDocumentType() != null) {
				if (document.getDocumentTypeCd() == null || !document.getDocumentTypeCd().equalsIgnoreCase(book.getDocumentType().getItemCd())) {
					throw new BusinessException(ErrorCode.BAD_REQUEST, "Loại văn bản không khớp với loại của sổ văn bản");
				}
			}

			if (book.getOrgNode() != null) {
				OrgNode issuingOrg = document.getIssuingOrgNode() != null ? document.getIssuingOrgNode() : document.getIssuingOrg();
				if (issuingOrg == null || issuingOrg.getId() == null || !issuingOrg.getId().equals(book.getOrgNode().getId())) {
					throw new BusinessException(ErrorCode.BAD_REQUEST, "Đơn vị ban hành của văn bản không khớp với sổ văn bản");
				}
			}

			if (book.getYear() != null) {
				LocalDate effDate = request.getEffectiveDate();
				if (effDate == null && document.getEffectiveDate() != null) {
					effDate = document.getEffectiveDate().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
				}
				if (effDate == null || effDate.getYear() != book.getYear().intValue()) {
					throw new BusinessException(ErrorCode.BAD_REQUEST, "Năm hiệu lực của văn bản không khớp với năm của sổ văn bản");
				}
			}
		}

		// 3. Tăng số thứ tự văn bản
		int nextNumber = book.getCurrentNumber() + 1;
		book.setCurrentNumber(nextNumber);
		documentBookRepository.save(book);

		// 4. Áp dụng pattern: "{NUM}/QĐ-ĐHEPU" -> "1/QĐ-ĐHEPU"
		String documentNumber = book.getNumberPattern().replace("{NUM}", String.valueOf(nextNumber));

		// Kiểm tra chống trùng lặp số hiệu trên hệ thống
		// Phân biệt 2 trường hợp: tạo mới (id == null) và cập nhật (id != null).
		// Khi id == null, SQL "id <> NULL" luôn trả về UNKNOWN, nên phải dùng query riêng.
		if (document != null) {
			boolean duplicateExists = document.getId() == null
					? documentRepository.existsByOfficialNumberAndDeleteFlgFalse(documentNumber)
					: documentRepository.existsByOfficialNumberAndIdNotAndDeleteFlgFalse(documentNumber, document.getId());
			if (duplicateExists) {
				throw new BusinessException(ErrorCode.DOCUMENT_NUMBER_ALREADY_EXISTS,
						"Số hiệu văn bản '" + documentNumber + "' đã tồn tại trên một văn bản khác trong hệ thống");
			}
		}

		// 5. Gán số văn bản và ngày hiệu lực vào Document entity
		if (document != null) {
			document.setOfficialNumber(documentNumber);
			document.setDocumentBook(book);
			if (request.getEffectiveDate() != null) {
				document.setEffectiveDate(request.getEffectiveDate());
			}
			// Bắt DataIntegrityViolationException từ DB unique constraint làm lưới an toàn
			// cuối cùng chống race condition khi existsBy... bị vượt qua bởi request đồng thời.
			// saveAndFlush() ép Hibernate flush ngay xuống DB trong phạm vi try-catch,
			// đảm bảo DataIntegrityViolationException từ unique constraint bị bắt tại đây
			// thay vì bị ném ra ngoài khi transaction commit (vượt khỏi tầm catch).
			try {
				documentRepository.saveAndFlush(document);
			} catch (DataIntegrityViolationException ex) {
				throw new BusinessException(ErrorCode.DOCUMENT_NUMBER_ALREADY_EXISTS,
						"Số hiệu văn bản '" + documentNumber + "' đã tồn tại trên một văn bản khác trong hệ thống");
			}
		}

		return AssignDocumentNumberResponse.builder()
				.documentNumber(documentNumber)
				.bookId(book.getId())
				.bookName(book.getBookName())
				.currentNumber(nextNumber)
				.effectiveDate(request.getEffectiveDate())
				.documentId(request.getDocumentId())
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public Page<DocumentResponse> getAssignedDocuments(Long bookId, String keyword, DocumentStatus status, Pageable pageable) {
		DocumentBook book = documentBookRepository.findByIdAndDeleteFlgFalse(bookId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_BOOK_NOT_FOUND));

		String typeCd = book.getDocumentType() != null ? book.getDocumentType().getItemCd() : null;
		Long orgId = book.getOrgNode() != null ? book.getOrgNode().getId() : null;
		String kw = null;
		if (keyword != null && !keyword.isBlank()) {
			kw = keyword.trim()
					.replace("\\", "\\\\")
					.replace("%", "\\%")
					.replace("_", "\\_");
		}

		Page<Document> docs = documentRepository.findAssignedDocuments(book.getId(), typeCd, orgId, status, kw, pageable);
		return docs.map(doc -> toDocumentDto(doc, book));
	}

	private DocumentResponse toDocumentDto(Document doc, DocumentBook book) {
		String typeName = doc.getDocumentTypeCd();
		if (doc.getDocumentTypeCd() != null) {
			typeName = dictionaryItemRepository.findByGroupCdAndItemCdAndDeleteFlgFalse("DOCUMENT_TYPE", doc.getDocumentTypeCd())
					.map(DictionaryItem::getNameVn)
					.orElse(doc.getDocumentTypeCd());
		}

		OrgNode org = doc.getIssuingOrgNode() != null ? doc.getIssuingOrgNode() : doc.getIssuingOrg();
		Long orgId = org != null ? org.getId() : null;
		String orgName = org != null ? org.getNameVn() : null;

		UserLogin creator = doc.getCreatorUser() != null ? doc.getCreatorUser() : doc.getDrafter();
		Long creatorId = creator != null ? creator.getId() : null;
		String creatorUsername = creator != null ? creator.getUsername() : null;

		DocumentStatus status = doc.getStatus() != null ? doc.getStatus() : DocumentStatus.DRAFT;

		Long bookId = book != null ? book.getId() : (doc.getDocumentBook() != null ? doc.getDocumentBook().getId() : null);
		String bookCode = book != null ? book.getBookCode() : (doc.getDocumentBook() != null ? doc.getDocumentBook().getBookCode() : null);

		return DocumentResponse.builder()
				.id(doc.getId())
				.draftCode(doc.getDraftCode())
				.title(doc.getTitle())
				.summary(doc.getSummary())
				.content(doc.getContent())
				.documentTypeCd(doc.getDocumentTypeCd())
				.documentTypeName(typeName)
				.issuingOrgNodeId(orgId)
				.issuingOrgNodeName(orgName)
				.creatorUserId(creatorId)
				.creatorUsername(creatorUsername)
				.status(status)
				.statusDescription(status.getDescription())
				.currentVersion(doc.getCurrentVersion())
				.securityLevel(doc.getSecurityLevel() != null ? doc.getSecurityLevel() : DocumentSecurityLevel.NORMAL)
				.urgencyLevel(doc.getUrgencyLevel() != null ? doc.getUrgencyLevel() : DocumentUrgencyLevel.NORMAL)
				.submittedAt(doc.getSubmittedAt())
				.createdDate(doc.getCreatedDate())
				.updatedDate(doc.getUpdatedDate())
				.officialNumber(doc.getOfficialNumber())
				.documentBookId(bookId)
				.documentBookCode(bookCode)
				.issueDate(doc.getIssueDate() != null ? LocalDate.ofInstant(doc.getIssueDate(), ZoneId.of("Asia/Ho_Chi_Minh")) : null)
				.effectiveDate(doc.getEffectiveDate() != null ? LocalDate.ofInstant(doc.getEffectiveDate(), ZoneId.of("Asia/Ho_Chi_Minh")) : null)
				.officialFilePath(doc.getOfficialFilePath())
				.officialHash(doc.getOfficialHash())
				.build();
	}

	private DocumentBookResponse toDto(DocumentBook entity) {
		return DocumentBookResponse.builder()
				.id(entity.getId())
				.bookCode(entity.getBookCode())
				.bookName(entity.getBookName())
				.year(entity.getYear())
				.bookLevel(entity.getBookLevel())
				.documentTypeId(entity.getDocumentType() != null ? entity.getDocumentType().getId() : null)
				.documentTypeName(entity.getDocumentType() != null ? entity.getDocumentType().getNameVn() : null)
				.documentTypeCd(entity.getDocumentType() != null ? entity.getDocumentType().getItemCd() : null)
				.orgNodeId(entity.getOrgNode() != null ? entity.getOrgNode().getId() : null)
				.orgNodeName(entity.getOrgNode() != null ? entity.getOrgNode().getNameVn() : null)
				.orgNodeCode(entity.getOrgNode() != null ? entity.getOrgNode().getCode() : null)
				.currentNumber(entity.getCurrentNumber())
				.numberPattern(entity.getNumberPattern())
				.isClosed(entity.getIsClosed())
				.createdDate(entity.getCreatedDate())
				.updatedDate(entity.getUpdatedDate())
				.createdBy(entity.getCreatedBy())
				.updatedBy(entity.getUpdatedBy())
				.build();
	}
}

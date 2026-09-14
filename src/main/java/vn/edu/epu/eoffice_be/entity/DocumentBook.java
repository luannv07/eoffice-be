package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

@Entity
@Table(name = "document_books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentBook extends BaseEntity {

	public static final int LEVEL_INSTITUTION = 1; // Cấp Đơn vị/Trường
	public static final int LEVEL_DEPARTMENT = 2;  // Cấp Phòng/Ban

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "document_books_seq")
	@SequenceGenerator(name = "document_books_seq", sequenceName = "document_books_seq", allocationSize = 50)
	Long id;

	@Column(name = "book_code", nullable = false, unique = true, length = 50)
	String bookCode;

	@Column(name = "book_name", nullable = false, length = 255)
	String bookName;

	@Column(name = "book_year", nullable = false)
	Integer year;

	@Min(1)
	@Max(2)
	@Column(name = "book_level", nullable = false)
	Integer bookLevel; // 1: Cấp Đơn vị/Trường, 2: Cấp Phòng/Ban

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_type_id", nullable = false)
	DictionaryItem documentType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "org_node_id", nullable = false)
	OrgNode orgNode;

	@Column(name = "current_number", nullable = false)
	@Builder.Default
	Integer currentNumber = 0;

	@Column(name = "number_pattern", nullable = false, length = 100)
	String numberPattern;

	@Column(name = "is_closed", nullable = false)
	@Builder.Default
	Boolean isClosed = false;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;

	@PrePersist
	void prePersistDefaults() {
		if (currentNumber == null) {
			currentNumber = 0;
		}
		if (isClosed == null) {
			isClosed = false;
		}
	}
}

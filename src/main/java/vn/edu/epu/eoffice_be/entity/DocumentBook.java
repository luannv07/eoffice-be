package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
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

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@Column(name = "book_code", nullable = false, unique = true, length = 50)
	String bookCode;

	@Column(name = "book_name", nullable = false, length = 255)
	String bookName;

	@Column(name = "book_year", nullable = false)
	Integer year;

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
}

package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

@Entity
@Table(name = "org_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrgNode extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	OrgNode parent;

	@Column(nullable = false, length = 50, unique = true)
	String code;

	@Column(name = "name_vn", nullable = false, length = 255)
	String nameVn;

	@Column(name = "name_en", nullable = false, length = 255)
	String nameEn;

	@Column(name = "type_cd", nullable = false, length = 50)
	String typeCd;

	@Column(name = "org_order")
	@Builder.Default
	Integer orgOrder = 0;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}
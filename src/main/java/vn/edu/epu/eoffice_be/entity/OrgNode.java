package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.annotation.TrackChange;
import vn.edu.epu.eoffice_be.common.annotation.TrackField;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

@Entity
@Table(name = "org_nodes")
@TrackChange
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

	@TrackField(label = "Mã đơn vị")
	@Column(nullable = false, length = 50, unique = true)
	String code;

	@TrackField(label = "Tên tiếng Việt")
	@Column(name = "name_vn", nullable = false, length = 255)
	String nameVn;

	@TrackField(label = "Tên tiếng Anh")
	@Column(name = "name_en", nullable = false, length = 255)
	String nameEn;

	@TrackField(label = "Loại đơn vị")
	@Column(name = "type_cd", nullable = false, length = 50)
	String typeCd;

	@TrackField(label = "Thứ tự sắp xếp")
	@Column(name = "org_order")
	@Builder.Default
	Integer orgOrder = 0;

	@TrackField(label = "Đã xóa")
	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}
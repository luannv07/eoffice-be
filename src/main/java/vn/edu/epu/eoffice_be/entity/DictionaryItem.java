package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

@Entity
@Table(name = "dictionary_items", uniqueConstraints = {
				@UniqueConstraint(name = "uq_dictionary_items_group_item", columnNames = {"group_cd", "item_cd"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DictionaryItem extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@Column(name = "group_cd", nullable = false, length = 50)
	String groupCd;

	@Column(name = "item_cd", nullable = false, length = 50)
	String itemCd;

	@Column(name = "name_vn", nullable = false, length = 255)
	String nameVn;

	@Column(name = "name_en", nullable = false, length = 255)
	String nameEn;

	@Column(name = "display_order", nullable = false)
	@Builder.Default
	int displayOrder = 0;

	@Column(name = "active_flg", nullable = false)
	@Builder.Default
	boolean activeFlg = true;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}
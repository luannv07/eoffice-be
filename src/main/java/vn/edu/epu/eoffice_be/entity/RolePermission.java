package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.Role;

@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RolePermission extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	Role role;

	@Column(name = "permission_cd", nullable = false, length = 100)
	String permissionCd;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}
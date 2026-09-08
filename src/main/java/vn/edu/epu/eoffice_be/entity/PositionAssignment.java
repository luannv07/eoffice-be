package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "position_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PositionAssignment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_login_id", nullable = false)
	UserLogin userLogin;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "org_node_id", nullable = false)
	OrgNode orgNode;

	@Column(name = "position_cd", nullable = false, length = 50)
	String positionCd;

	@Column(name = "effective_from", nullable = false)
	Instant effectiveFrom;

	@Column(name = "effective_to")
	Instant effectiveTo;

	@Column(name = "actual_dismissal_date")
	Instant actualDismissalDate;

	@Column(name = "dismissal_reason_cd", length = 50)
	String dismissalReasonCd;

	@Column(name = "dismissal_note", columnDefinition = "TEXT")
	String dismissalNote;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}
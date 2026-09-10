package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "user_infos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserInfo extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_login_id", nullable = false)
	UserLogin userLogin;

	@Column(name = "full_name", nullable = false, length = 255)
	String fullName;

	@Column(name = "date_of_birth")
	LocalDate dateOfBirth;

	@Column(length = 10)
	String gender;

	@Column(name = "national_id_number", length = 20)
	String nationalIdNumber;

	@Column(name = "avatar_url", length = 500)
	String avatarUrl;

	@Column(name = "contact_email", unique = true, length = 255)
	String contactEmail;

	@Column(name = "contact_phone", length = 20)
	String contactPhone;

	@Column(length = 500)
	String address;

	@Column(name = "employee_code", unique = true, length = 50)
	String employeeCode;

	@Column(name = "academic_title", length = 50)
	String academicTitle;

	@Column(name = "academic_degree", length = 50)
	String academicDegree;

	@Column(name = "party_membership_date")
	LocalDate partyMembershipDate;

	@Column(name = "employment_status_cd", nullable = false, length = 20)
	@Builder.Default
	String employmentStatusCd = "ACTIVE";

	@Column(name = "join_org_date")
	LocalDate joinOrgDate;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}
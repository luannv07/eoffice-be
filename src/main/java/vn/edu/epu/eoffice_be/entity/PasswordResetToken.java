package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PasswordResetToken extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_login_id", nullable = false)
	UserLogin userLogin;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	String tokenHash;

	@Column(name = "expires_at", nullable = false)
	Instant expiresAt;

	@Column(name = "used_at")
	Instant usedAt;

	@Column(name = "delete_flg", nullable = false)
	@Builder.Default
	boolean deleteFlg = false;
}

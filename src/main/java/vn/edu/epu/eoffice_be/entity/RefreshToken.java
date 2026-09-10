package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
	@SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
	Long id;

	@Column(name = "user_id", nullable = false)
	Long userId;

	@Column(nullable = false, length = 36, unique = true)
	String rt;

	@Column(name = "expires_at", nullable = false)
	Instant expiresAt;

	@Column(name = "is_revoked", nullable = false)
	@Builder.Default
	boolean revoked = false;
}

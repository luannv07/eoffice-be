package vn.edu.epu.eoffice_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.common.entity.BaseEntity;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.UserStatusCd;

import java.time.Instant;

@Entity
@Table(name = "user_logins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserLogin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eoffice_seq")
    @SequenceGenerator(name = "eoffice_seq", sequenceName = "hibernate_sequence", allocationSize = 50)
    Long id;

    @Column(nullable = false, length = 100, unique = true)
    String username;

    @Column(length = 255, unique = true)
    String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    Role role = Role.USER;

    @Column(name = "user_status_cd", nullable = false, length = 20)
    @Builder.Default
    String userStatusCd = UserStatusCd.ACTIVE.name();

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    boolean mustChangePassword = false;

    @Column(name = "password_expired_at")
    Instant passwordExpiredAt;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    int failedLoginAttempts = 0;

    @Column(name = "lockout_until")
    Instant lockoutUntil;

    @Column(name = "last_login_at")
    Instant lastLoginAt;

    @Column(name = "delete_flg", nullable = false)
    @Builder.Default
    boolean deleteFlg = false;
}
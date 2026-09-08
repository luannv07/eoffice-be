package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.UserStatusCd;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.ChangeRoleRequest;
import vn.edu.epu.eoffice_be.dto.request.CreateUserRequest;
import vn.edu.epu.eoffice_be.dto.request.UpdateUserRequest;
import vn.edu.epu.eoffice_be.dto.request.UserFilterRequest;
import vn.edu.epu.eoffice_be.dto.response.UserDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.UserSummaryResponse;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserManagementService {

    final UserLoginRepository userLoginRepository;
    final UserInfoRepository userInfoRepository;
    final PasswordEncoder passwordEncoder;
    final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> list(UserFilterRequest filter, Pageable pageable) {
        Specification<UserLogin> spec = filter.toSpecification();
        return userLoginRepository.findAll(spec, pageable)
                .map(this::toUserSummary);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getDetail(Long id) {
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return toUserDetail(user);
    }

    @Transactional
    public UserDetailResponse create(CreateUserRequest request) {
        Role actorRole = currentActorRole();
        if (request.getRole() == Role.SYSTEM_ADMIN && actorRole != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        ensureUnique(username, email);

        UserLogin user = UserLogin.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .userStatusCd(UserStatusCd.ACTIVE.name())
                .mustChangePassword(request.isMustChangePassword())
                .build();
        user = userLoginRepository.save(user);
        return toUserDetail(user);
    }

    @Transactional
    public UserDetailResponse update(Long id, UpdateUserRequest request) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        assertActorCanManage(user, actorRole);

        if (request.getRole() == Role.SYSTEM_ADMIN && actorRole != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (request.getUsername() != null && !request.getUsername().isBlank()
                && !request.getUsername().trim().equalsIgnoreCase(user.getUsername())) {
            ensureUniqueUsername(request.getUsername().trim(), user.getId());
            user.setUsername(request.getUsername().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
            ensureUniqueEmail(request.getEmail().trim(), user.getId());
            user.setEmail(request.getEmail().trim());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getMustChangePassword() != null) {
            user.setMustChangePassword(request.getMustChangePassword());
        }

        user = userLoginRepository.save(user);
        return toUserDetail(user);
    }

    @Transactional
    public void delete(Long id) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);
        assertNotLastSystemAdmin(user);

        user.setDeleteFlg(true);
        userLoginRepository.save(user);
        refreshTokenService.revokeAllActive(user.getId());
    }

    @Transactional
    public void restore(Long id) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);

        user.setDeleteFlg(false);
        userLoginRepository.save(user);
    }

    @Transactional
    public void lock(Long id) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);

        user.setUserStatusCd(UserStatusCd.LOCKED.name());
        user.setLockoutUntil(null);
        user = userLoginRepository.save(user);
        refreshTokenService.revokeAllActive(user.getId());
    }

    @Transactional
    public void unlock(Long id) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);

        user.setUserStatusCd(UserStatusCd.ACTIVE.name());
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userLoginRepository.save(user);
    }

    @Transactional
    public void ban(Long id) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);
        assertNotLastSystemAdmin(user);

        user.setUserStatusCd(UserStatusCd.DISABLED.name());
        user = userLoginRepository.save(user);
        refreshTokenService.revokeAllActive(user.getId());
    }

    @Transactional
    public void activate(Long id) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);

        user.setUserStatusCd(UserStatusCd.ACTIVE.name());
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user = userLoginRepository.save(user);
        refreshTokenService.revokeAllActive(user.getId());
    }

    @Transactional
    public UserDetailResponse changeRole(Long id, ChangeRoleRequest request) {
        Role actorRole = currentActorRole();
        UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertActorCanManage(user, actorRole);

        Role newRole = request.getRole();
        if (newRole == Role.SYSTEM_ADMIN && actorRole != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (user.getRole() == Role.SYSTEM_ADMIN && newRole != Role.SYSTEM_ADMIN) {
            assertNotLastSystemAdmin(user);
        }

        user.setRole(newRole);
        user = userLoginRepository.save(user);
        return toUserDetail(user);
    }

    private void assertActorCanManage(UserLogin target, Role actorRole) {
        if (target.getRole() == Role.SYSTEM_ADMIN && actorRole != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void assertNotLastSystemAdmin(UserLogin target) {
        if (target.getRole() == Role.SYSTEM_ADMIN
                && userLoginRepository.countByRoleAndDeleteFlgFalse(Role.SYSTEM_ADMIN, false) <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR);
        }
    }

    private void ensureUnique(String username, String email) {
        ensureUniqueUsername(username, null);
        ensureUniqueEmail(email, null);
    }

    private void ensureUniqueUsername(String username, Long exceptId) {
        userLoginRepository.findByUsernameAndDeleteFlgFalse(username).ifPresent(existing -> {
            if (exceptId == null || !existing.getId().equals(exceptId)) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
        });
    }

    private void ensureUniqueEmail(String email, Long exceptId) {
        userLoginRepository.findByEmailAndDeleteFlgFalse(email).ifPresent(existing -> {
            if (exceptId == null || !existing.getId().equals(exceptId)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        });
    }

    private Role currentActorRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtUserPrincipal jwtPrincipal) {
            return Role.from(jwtPrincipal.getRole());
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private UserSummaryResponse toUserSummary(UserLogin user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userStatusCd(user.getUserStatusCd())
                .mustChangePassword(user.isMustChangePassword())
                .createdDate(user.getCreatedDate())
                .updatedDate(user.getUpdatedDate())
                .build();
    }

    private UserDetailResponse toUserDetail(UserLogin user) {
        UserInfo info = userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(user.getId()).orElse(null);
        UserDetailResponse.UserDetailResponseBuilder builder = UserDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .userStatusCd(user.getUserStatusCd())
                .mustChangePassword(user.isMustChangePassword())
                .passwordExpiredAt(user.getPasswordExpiredAt())
                .lockoutUntil(user.getLockoutUntil())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lastLoginAt(user.getLastLoginAt())
                .deleted(user.isDeleteFlg())
                .profileCompleted(info != null)
                .createdDate(user.getCreatedDate())
                .updatedDate(user.getUpdatedDate());
        if (info != null) {
            builder.fullName(info.getFullName())
                    .employeeCode(info.getEmployeeCode())
                    .dateOfBirth(info.getDateOfBirth())
                    .gender(info.getGender())
                    .contactEmail(info.getContactEmail())
                    .contactPhone(info.getContactPhone())
                    .address(info.getAddress())
                    .academicTitle(info.getAcademicTitle())
                    .academicDegree(info.getAcademicDegree())
                    .joinOrgDate(info.getJoinOrgDate())
                    .employmentStatusCd(info.getEmploymentStatusCd());
        }
        return builder.build();
    }
}

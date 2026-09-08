package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    UserLoginRepository userLoginRepository;
    @Mock
    UserInfoRepository userInfoRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    RefreshTokenService refreshTokenService;

    @InjectMocks
    UserManagementService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setActor(Role role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(999L, "actor", role.name(), true, List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", List.of()));
    }

    private UserLogin user(Long id, String username, Role role, String status) {
        return UserLogin.builder()
                .id(id)
                .username(username)
                .email(username + "@epu.edu.vn")
                .passwordHash("hash")
                .role(role)
                .userStatusCd(status)
                .build();
    }

    private UserInfo userInfo(Long id, UserLogin login, String name) {
        return UserInfo.builder().id(id).userLogin(login).fullName(name).build();
    }

    private void stubUserInfoSave() {
        when(userInfoRepository.save(any(UserInfo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void listShouldReturnPageOfSummaries() {
        setActor(Role.ADMIN);
        UserLogin u = user(1L, "alice", Role.USER, UserStatusCd.ACTIVE.name());
        when(userLoginRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(u)));
        UserFilterRequest filter = new UserFilterRequest();
        Page<UserSummaryResponse> result = service.list(filter, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertEquals("alice", result.getContent().get(0).getUsername());
    }

    @Test
    void getDetailShouldReturnNotFoundWhenMissing() {
        setActor(Role.ADMIN);
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDetail(1L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createShouldForbidAdminCreatingSystemAdmin() {
        setActor(Role.ADMIN);
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("bob");
        req.setEmail("bob@epu.edu.vn");
        req.setPassword("MatKhau@2026");
        req.setRole(Role.SYSTEM_ADMIN);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(userLoginRepository, never()).save(any());
    }

    @Test
    void createShouldSucceedForAdminCreatingUser() {
        setActor(Role.ADMIN);
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("bob");
        req.setEmail("bob@epu.edu.vn");
        req.setPassword("MatKhau@2026");
        req.setRole(Role.USER);
        when(userLoginRepository.findByUsernameAndDeleteFlgFalse("bob")).thenReturn(Optional.empty());
        when(userLoginRepository.findByEmailAndDeleteFlgFalse("bob@epu.edu.vn")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("MatKhau@2026")).thenReturn("encoded");
        when(userLoginRepository.save(any(UserLogin.class))).thenAnswer(i -> i.getArgument(0));
        UserDetailResponse result = service.create(req);
        assertEquals("bob", result.getUsername());
        assertEquals(Role.USER, result.getRole());
    }

    @Test
    void deleteShouldForbidRemovingLastSystemAdmin() {
        setActor(Role.SYSTEM_ADMIN);
        UserLogin sysAdmin = user(2L, "root", Role.SYSTEM_ADMIN, UserStatusCd.ACTIVE.name());
        when(userLoginRepository.findByIdAndDeleteFlgFalse(2L)).thenReturn(Optional.of(sysAdmin));
        when(userLoginRepository.countByRoleAndDeleteFlgFalse(Role.SYSTEM_ADMIN)).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(2L));
        assertEquals(ErrorCode.BUSINESS_ERROR, ex.getErrorCode());
        verify(userLoginRepository, never()).save(any());
    }

    @Test
    void adminCannotDeleteSystemAdmin() {
        setActor(Role.ADMIN);
        UserLogin sysAdmin = user(2L, "root", Role.SYSTEM_ADMIN, UserStatusCd.ACTIVE.name());
        when(userLoginRepository.findByIdAndDeleteFlgFalse(2L)).thenReturn(Optional.of(sysAdmin));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(2L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void banShouldRevokeTokensAndSetDisabled() {
        setActor(Role.ADMIN);
        UserLogin u = user(3L, "carol", Role.USER, UserStatusCd.ACTIVE.name());
        when(userLoginRepository.findByIdAndDeleteFlgFalse(3L)).thenReturn(Optional.of(u));
        when(userLoginRepository.save(any(UserLogin.class))).thenAnswer(i -> i.getArgument(0));
        service.ban(3L);
        assertEquals(UserStatusCd.DISABLED.name(), u.getUserStatusCd());
        verify(refreshTokenService, times(1)).revokeAllActive(3L);
    }

    @Test
    void changeRoleShouldForbidAdminPromotingToSystemAdmin() {
        setActor(Role.ADMIN);
        UserLogin u = user(4L, "dave", Role.USER, UserStatusCd.ACTIVE.name());
        when(userLoginRepository.findByIdAndDeleteFlgFalse(4L)).thenReturn(Optional.of(u));
        ChangeRoleRequest req = new ChangeRoleRequest();
        req.setRole(Role.SYSTEM_ADMIN);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeRole(4L, req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void deleteShouldNotFailWhenNoProfileExists() {
        setActor(Role.ADMIN);
        UserLogin u = user(6L, "frank", Role.USER, UserStatusCd.ACTIVE.name());
        when(userLoginRepository.findByIdAndDeleteFlgFalse(6L)).thenReturn(Optional.of(u));
        when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(6L)).thenReturn(Optional.empty());
        when(userLoginRepository.save(any(UserLogin.class))).thenAnswer(i -> i.getArgument(0));

        service.delete(6L);

        assertEquals(true, u.isDeleteFlg());
        verify(userInfoRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void restoreShouldUnsetDeleteFlag() {
        setActor(Role.ADMIN);
        UserLogin u = user(5L, "eve", Role.USER, UserStatusCd.ACTIVE.name());
        u.setDeleteFlg(true);
        when(userLoginRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userInfoRepository.findByUserLoginId(5L)).thenReturn(Optional.empty());
        when(userLoginRepository.save(any(UserLogin.class))).thenAnswer(i -> i.getArgument(0));
        service.restore(5L);
        assertEquals(false, u.isDeleteFlg());
        verify(userLoginRepository, times(1)).save(u);
    }

    @Test
    void deleteShouldCascadeSoftDeleteUserInfo() {
        setActor(Role.ADMIN);
        UserLogin u = user(5L, "eve", Role.USER, UserStatusCd.ACTIVE.name());
        UserInfo info = userInfo(10L, u, "Eve");
        when(userLoginRepository.findByIdAndDeleteFlgFalse(5L)).thenReturn(Optional.of(u));
        when(userInfoRepository.findByUserLoginIdAndDeleteFlgFalse(5L)).thenReturn(Optional.of(info));
        when(userLoginRepository.save(any(UserLogin.class))).thenAnswer(i -> i.getArgument(0));
        stubUserInfoSave();

        service.delete(5L);

        assertEquals(true, u.isDeleteFlg());
        assertEquals(true, info.isDeleteFlg());
        verify(userInfoRepository, times(1)).save(info);
    }

    @Test
    void restoreShouldCascadeUnsetUserInfoDeleteFlag() {
        setActor(Role.ADMIN);
        UserLogin u = user(5L, "eve", Role.USER, UserStatusCd.ACTIVE.name());
        u.setDeleteFlg(true);
        UserInfo info = userInfo(10L, u, "Eve");
        info.setDeleteFlg(true);
        when(userLoginRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userInfoRepository.findByUserLoginId(5L)).thenReturn(Optional.of(info));
        when(userLoginRepository.save(any(UserLogin.class))).thenAnswer(i -> i.getArgument(0));
        stubUserInfoSave();

        service.restore(5L);

        assertEquals(false, u.isDeleteFlg());
        assertEquals(false, info.isDeleteFlg());
        verify(userInfoRepository, times(1)).save(info);
    }
}

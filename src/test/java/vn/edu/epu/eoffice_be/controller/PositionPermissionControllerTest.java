// Kiểm thử API gán quyền ký nháy với role hiện hành từ database.
package vn.edu.epu.eoffice_be.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.common.exception.GlobalExceptionHandler;
import vn.edu.epu.eoffice_be.dto.request.PositionDraftSignRequest;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.PositionPermission;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.service.DocumentReadPolicy;
import vn.edu.epu.eoffice_be.service.PositionPermissionService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gọi controller và service thật, chỉ mock dữ liệu lưu trữ.
@ExtendWith(MockitoExtension.class)
class PositionPermissionControllerTest {
    @Mock private UserLoginRepository users;
    @Mock private DocumentApprovalRequestRepository requests;
    @Mock private DictionaryItemRepository dictionaries;
    @Mock private PositionPermissionRepository permissions;
    private PositionPermissionService service;
    private MockMvc mvc;

    // Dựng API cùng policy đọc tài khoản thật.
    @BeforeEach
    void setUp() {
        DocumentReadPolicy readers = new DocumentReadPolicy(users, requests);
        service = new PositionPermissionService(readers, dictionaries, permissions);
        mvc = MockMvcBuilders.standaloneSetup(new PositionPermissionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // Dọn ngữ cảnh xác thực sau từng ca.
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Tạo JWT và tài khoản database có thể mang hai role khác nhau.
    private UserLogin authenticate(Role databaseRole, Role jwtRole) {
        UserLogin actor = UserLogin.builder().id(10L).username("operator")
                .role(databaseRole).userStatusCd("ACTIVE").deleteFlg(false).build();
        when(users.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(actor));
        JwtUserPrincipal principal = new JwtUserPrincipal(10L, "operator", jwtRole.name(), true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        return actor;
    }

    // Chuẩn bị chức vụ đang hoạt động và lưu bản ghi mới.
    private void preparePosition() {
        when(dictionaries.findPositionForPermissionUpdate("TP"))
                .thenReturn(Optional.of(DictionaryItem.builder().itemCd("TP").activeFlg(true).build()));
        when(permissions.save(any(PositionPermission.class))).thenAnswer(invocation -> {
            PositionPermission permission = invocation.getArgument(0);
            if (permission.getId() == null) {
                permission.setId(80L);
            }
            return permission;
        });
    }

    // Chỉ hai role quản trị trong database được gán quyền qua HTTP.
    @ParameterizedTest
    @EnumSource(Role.class)
    void databaseRoleDeterminesAccessEvenWithAdminJwt(Role databaseRole) throws Exception {
        authenticate(databaseRole, Role.ADMIN);
        boolean allowed = databaseRole == Role.ADMIN || databaseRole == Role.SYSTEM_ADMIN;
        if (allowed) {
            preparePosition();
        }
        var response = mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":false}"));
        if (allowed) {
            response.andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.positionCd").value("TP"))
                    .andExpect(jsonPath("$.data.permissionCd").value("DOCUMENT_SIGN_DRAFT"))
                    .andExpect(jsonPath("$.data.inheritToChildren").value(false));
        } else {
            response.andExpect(status().isForbidden());
            verifyNoInteractions(dictionaries, permissions);
        }
    }

    // Service trực tiếp cũng không cho role nghiệp vụ gán quyền.
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SYSTEM_ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void directServiceCallCannotBypassAuthorization(Role role) {
        authenticate(role, Role.SYSTEM_ADMIN);
        PositionDraftSignRequest request = new PositionDraftSignRequest();
        request.setInheritToChildren(false);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.assignDraftSign("TP", request));
        assertEquals(ErrorCode.FORBIDDEN, failure.getErrorCode());
        verifyNoInteractions(dictionaries, permissions);
    }

    // Role vừa được nâng trong database có hiệu lực dù JWT vẫn là USER.
    @Test
    void databaseAdminWithOldUserJwtCanAssign() throws Exception {
        authenticate(Role.ADMIN, Role.USER);
        preparePosition();
        mvc.perform(put("/position-permissions/tp/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inheritToChildren").value(true));
        verify(dictionaries).findPositionForPermissionUpdate("TP");
    }

    // Gán lại cập nhật cùng bản ghi thay vì tạo bản ghi mới.
    @Test
    void repeatedAssignmentUpdatesExistingPermission() throws Exception {
        authenticate(Role.SYSTEM_ADMIN, Role.SYSTEM_ADMIN);
        preparePosition();
        PositionPermission existing = PositionPermission.builder().id(91L).positionCd("TP")
                .permissionCd("DOCUMENT_SIGN_DRAFT").inheritToChildren(false).build();
        when(permissions.findByPositionCdAndPermissionCdAndDeleteFlgFalse("TP", "DOCUMENT_SIGN_DRAFT"))
                .thenReturn(Optional.of(existing));
        mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(91));
        verify(permissions).save(existing);
        assertTrue(existing.isInheritToChildren());
    }

    // Chặn chức vụ không tồn tại hoặc đã bị xóa mềm.
    @Test
    void unknownPositionCannotReceivePermission() throws Exception {
        authenticate(Role.ADMIN, Role.ADMIN);
        mvc.perform(put("/position-permissions/UNKNOWN/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("POSITION_CD_INVALID"));
        verifyNoInteractions(permissions);
    }

    // Chặn chức vụ ngừng hoạt động.
    @Test
    void inactivePositionCannotReceivePermission() throws Exception {
        authenticate(Role.ADMIN, Role.ADMIN);
        when(dictionaries.findPositionForPermissionUpdate("TP"))
                .thenReturn(Optional.of(DictionaryItem.builder().activeFlg(false).build()));
        mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":false}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(permissions);
    }

    // Chặn thiếu phạm vi kế thừa trước khi vào service.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"inheritToChildren\":null}"})
    void inheritanceMustBeExplicit(String body) throws Exception {
        mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(users, dictionaries, permissions);
    }

    // Tài khoản đã khóa không được gán quyền dù còn JWT quản trị.
    @Test
    void disabledAdminCannotAssign() throws Exception {
        UserLogin actor = authenticate(Role.ADMIN, Role.ADMIN);
        actor.setUserStatusCd("INACTIVE");
        mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dictionaries, permissions);
    }

    // Không cấp quyền khi tài khoản đã xóa hoặc không còn trong database.
    @Test
    void missingAccountCannotAssign() throws Exception {
        authenticate(Role.ADMIN, Role.ADMIN);
        when(users.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.empty());
        mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":false}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dictionaries, permissions);
    }

    // Chặn lời gọi không có người đăng nhập.
    @Test
    void unauthenticatedCallCannotAssign() throws Exception {
        mvc.perform(put("/position-permissions/TP/draft-sign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inheritToChildren\":false}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, dictionaries, permissions);
    }
}

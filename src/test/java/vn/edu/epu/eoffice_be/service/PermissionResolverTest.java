package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.PositionPermission;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.RolePermissionCatalog;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionResolverTest {

    @Mock
    private PositionAssignmentRepository positionAssignmentRepository;

    @Mock
    private PositionPermissionRepository positionPermissionRepository;

    @Mock
    private PositionDegRepository positionDegRepository;

    @Mock
    private OrgNodeRepository orgNodeRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionResolver permissionResolver;

    private JwtUserPrincipal normalPrincipal;
    private JwtUserPrincipal adminPrincipal;
    private OrgNode node1;

    @BeforeEach
    void setUp() {
        normalPrincipal = new JwtUserPrincipal(10L, "testuser", "USER", true, Collections.emptyList());
        adminPrincipal = new JwtUserPrincipal(1L, "admin", "SYSTEM_ADMIN", true, Collections.emptyList());
        node1 = OrgNode.builder().id(100L).build();
    }

    @Test
    @DisplayName("resolvePermissions trả về rỗng khi principal hoặc userId là null")
    void resolvePermissions_nullPrincipalOrUserId_shouldReturnEmpty() {
        assertTrue(permissionResolver.resolvePermissions(null).isEmpty());

        JwtUserPrincipal nullIdPrincipal = new JwtUserPrincipal(null, "anon", "USER", true, Collections.emptyList());
        assertTrue(permissionResolver.resolvePermissions(nullIdPrincipal).isEmpty());
    }

    @Test
    @DisplayName("resolvePermissions kết hợp quyền từ Position và Role")
    void resolvePermissions_validPrincipal_shouldCombinePositionAndRole() {
        PositionAssignment assignment = PositionAssignment.builder()
                .positionCd("TRUONG_PHONG")
                .orgNode(node1)
                .build();
        when(positionAssignmentRepository.findActiveAssignments(eq(10L), any(Instant.class)))
                .thenReturn(List.of(assignment));

        PositionPermission pp = PositionPermission.builder()
                .positionCd("TRUONG_PHONG")
                .permissionCd("doc:approve")
                .build();
        when(positionPermissionRepository.findByPositionCdAndDeleteFlgFalse("TRUONG_PHONG"))
                .thenReturn(List.of(pp));

        when(rolePermissionRepository.findPermissionCodesByRole(Role.USER))
                .thenReturn(List.of("doc:read"));

        Set<String> perms = permissionResolver.resolvePermissions(normalPrincipal);

        assertEquals(2, perms.size());
        assertTrue(perms.contains("doc:approve"));
        assertTrue(perms.contains("doc:read"));
    }

    @Test
    @DisplayName("hasPermission trả về false khi principal hoặc permissionCd rỗng")
    void hasPermission_nullOrBlank_shouldReturnFalse() {
        assertFalse(permissionResolver.hasPermission(null, "doc:read", 100L));
        assertFalse(permissionResolver.hasPermission(normalPrincipal, null, 100L));
        assertFalse(permissionResolver.hasPermission(normalPrincipal, "   ", 100L));
    }

    @Test
    @DisplayName("hasPermission trả về true ngay lập tức với SYSTEM_ADMIN")
    void hasPermission_systemAdmin_shouldReturnTrue() {
        assertTrue(permissionResolver.hasPermission(adminPrincipal, "any:action", 100L));
        assertTrue(permissionResolver.hasPermission(adminPrincipal, "any:action", null));
    }

    @Test
    @DisplayName("hasPermission kiểm tra quyền global theo role")
    void hasPermission_globalRolePermission_shouldReturnTrue() {
        JwtUserPrincipal orgAdminPrincipal = new JwtUserPrincipal(2L, "manager", "ADMIN", true, Collections.emptyList());
        assertTrue(permissionResolver.hasPermission(orgAdminPrincipal, RolePermissionCatalog.PERM_ORG_READ, null));
    }

    @Test
    @DisplayName("hasPermission kiểm tra quyền position trong cùng node không kế thừa")
    void hasPermission_positionScope_noInherit() {
        PositionAssignment assignment = PositionAssignment.builder()
                .positionCd("CHUYEN_VIEN")
                .orgNode(node1)
                .build();
        when(positionAssignmentRepository.findActiveAssignments(eq(10L), any(Instant.class)))
                .thenReturn(List.of(assignment));

        PositionPermission pp = PositionPermission.builder()
                .positionCd("CHUYEN_VIEN")
                .permissionCd("doc:create")
                .inheritToChildren(false)
                .build();
        when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse("CHUYEN_VIEN", "doc:create"))
                .thenReturn(Optional.of(pp));

        // orgNodeId trùng với assigned node -> true
        assertTrue(permissionResolver.hasPermission(normalPrincipal, "doc:create", 100L));

        // orgNodeId khác assigned node -> false (khi không có delegation)
        assertFalse(permissionResolver.hasPermission(normalPrincipal, "doc:create", 200L));

        // orgNodeId null -> true
        assertTrue(permissionResolver.hasPermission(normalPrincipal, "doc:create", null));
    }

    @Test
    @DisplayName("hasPermission kiểm tra quyền position có kế thừa cho node con")
    void hasPermission_positionScope_withInherit() {
        PositionAssignment assignment = PositionAssignment.builder()
                .positionCd("GIAM_DOC")
                .orgNode(node1)
                .build();
        when(positionAssignmentRepository.findActiveAssignments(eq(10L), any(Instant.class)))
                .thenReturn(List.of(assignment));

        PositionPermission pp = PositionPermission.builder()
                .positionCd("GIAM_DOC")
                .permissionCd("doc:publish")
                .inheritToChildren(true)
                .build();
        when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse("GIAM_DOC", "doc:publish"))
                .thenReturn(Optional.of(pp));

        when(orgNodeRepository.findActiveDescendantIds(100L)).thenReturn(List.of(101L, 102L));

        // Node con nằm trong descendantIds -> true
        assertTrue(permissionResolver.hasPermission(normalPrincipal, "doc:publish", 101L));

        // Node không nằm trong descendantIds và không phải node1 -> false (khi delegation cũng false)
        assertFalse(permissionResolver.hasPermission(normalPrincipal, "doc:publish", 999L));
    }

    @Test
    @DisplayName("hasPermission kiểm tra quyền qua uỷ quyền (delegation)")
    void hasPermission_delegation_shouldReturnTrue() {
        when(positionAssignmentRepository.findActiveAssignments(eq(10L), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        when(positionDegRepository.hasValidDelegation(eq(10L), eq(100L), eq("doc:sign"), any(Instant.class)))
                .thenReturn(true);

        assertTrue(permissionResolver.hasPermission(normalPrincipal, "doc:sign", 100L));
    }
}

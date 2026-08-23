package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.PositionPermission;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionDegRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.RolePermissionRepository;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.security.RolePermissionCatalog;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionResolver {

    final PositionAssignmentRepository positionAssignmentRepository;
    final PositionPermissionRepository positionPermissionRepository;
    final PositionDegRepository positionDegRepository;
    final OrgNodeRepository orgNodeRepository;
    final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public Set<String> resolvePermissions(JwtUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return Collections.emptySet();
        }
        Set<String> permissions = resolvePositionPermissions(principal.getUserId());
        permissions.addAll(resolveRolePermissions(principal.getRole()));
        return permissions;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(JwtUserPrincipal principal, String permissionCd, Long orgNodeId) {
        if (principal == null || permissionCd == null || permissionCd.isBlank()) {
            return false;
        }
        Role role = resolveRole(principal.getRole());
        if (role == Role.SYSTEM_ADMIN) {
            return true;
        }

        // Đường 0: role global, không phụ thuộc orgNodeId
        if (RolePermissionCatalog.hasGlobalPermission(role, permissionCd)) {
            return true;
        }

        // Đường 1: position-based. orgNodeId dùng để lọc theo phạm vi position_assignments
        // (qua recursive CTE trên org_nodes.parent_id), KHÔNG truyền vào PositionPermission
        // query (bảng đó giờ global theo position_cd). orgNodeId null => chỉ xét quyền không
        // phụ thuộc node.
        List<PositionAssignment> assignments =
                positionAssignmentRepository.findActiveAssignments(principal.getUserId(), Instant.now());
        for (PositionAssignment pa : assignments) {
            if (!isPositionEffectiveWithinScope(pa.getOrgNode(), pa.getPositionCd(), permissionCd, orgNodeId)) {
                continue;
            }
            return true;
        }

        // Đường 2: delegation (uỷ quyền). orgNodeId khớp trực tiếp position_degs.org_node_id.
        if (orgNodeId != null && positionDegRepository.hasValidDelegation(
                principal.getUserId(), orgNodeId, permissionCd, Instant.now())) {
            return true;
        }

        return false;
    }

    private Set<String> resolvePositionPermissions(Long userId) {
        Set<String> permissions = new HashSet<>();
        List<PositionAssignment> assignments =
                positionAssignmentRepository.findActiveAssignments(userId, Instant.now());
        for (PositionAssignment assignment : assignments) {
            positionPermissionRepository.findByPositionCdAndDeleteFlgFalse(assignment.getPositionCd())
                    .forEach(pp -> permissions.add(pp.getPermissionCd()));
        }
        return permissions;
    }

    /**
     * Kiểm tra permission đã được định nghĩa cho position và orgNodeId có nằm trong phạm vi
     * của position tại node đó hay không (chính node, hoặc node con nếu inheritToChildren).
     */
    private boolean isPositionEffectiveWithinScope(OrgNode assignedNode, String positionCd,
                                                   String permissionCd, Long orgNodeId) {
        Optional<PositionPermission> definition =
                positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse(positionCd, permissionCd);
        if (definition.isEmpty()) {
            return false;
        }
        if (orgNodeId == null) {
            return true;
        }
        PositionPermission pp = definition.get();
        if (!pp.isInheritToChildren()) {
            return assignedNode.getId().equals(orgNodeId);
        }
        Set<Long> scope = new HashSet<>(orgNodeRepository.findActiveDescendantIds(assignedNode.getId()));
        scope.add(assignedNode.getId());
        return scope.contains(orgNodeId);
    }

    private Set<String> resolveRolePermissions(String roleName) {
        Role role = resolveRole(roleName);
        return new HashSet<>(rolePermissionRepository.findPermissionCodesByRole(role));
    }

    private Role resolveRole(String roleName) {
        return Role.from(roleName, Role.USER);
    }
}

package vn.edu.epu.eoffice_be.auth.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.auth.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.auth.security.RolePermissionCatalog;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.RolePermissionRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionResolver {

    final PositionAssignmentRepository positionAssignmentRepository;
    final PositionPermissionRepository positionPermissionRepository;
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

        Set<String> permissions = new HashSet<>();
        permissions.addAll(resolvePositionPermissions(principal.getUserId()));
        permissions.addAll(resolveRolePermissions(principal.getRole()));

        if (permissions.contains(permissionCd)) {
            return true;
        }
        return orgNodeId == null && RolePermissionCatalog.hasGlobalPermission(role, permissionCd);
    }

    private Set<String> resolvePositionPermissions(Long userId) {
        Set<String> permissions = new HashSet<>();
        List<PositionAssignment> assignments =
                positionAssignmentRepository.findActiveAssignments(userId, Instant.now());
        for (PositionAssignment assignment : assignments) {
            Long orgNodeId = assignment.getOrgNode().getId();
            positionPermissionRepository.findGrantedPermissionCodes(orgNodeId, assignment.getPositionCd())
                    .forEach(permissions::add);
            positionPermissionRepository.findInheritedGrantedPermissionCodes(orgNodeId, assignment.getPositionCd())
                    .forEach(permissions::add);
        }
        return permissions;
    }

    private Set<String> resolveRolePermissions(String roleName) {
        Role role = resolveRole(roleName);
        return new HashSet<>(rolePermissionRepository.findPermissionCodesByRole(role));
    }

    private Role resolveRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Role.USER;
        }
        try {
            return Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return Role.USER;
        }
    }
}
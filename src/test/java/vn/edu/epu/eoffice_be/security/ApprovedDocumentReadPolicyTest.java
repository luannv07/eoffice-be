package vn.edu.epu.eoffice_be.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.PositionAssignment;
import vn.edu.epu.eoffice_be.entity.PositionPermission;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.PositionAssignmentRepository;
import vn.edu.epu.eoffice_be.repository.PositionDegRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;
import vn.edu.epu.eoffice_be.repository.RolePermissionRepository;
import vn.edu.epu.eoffice_be.service.PermissionResolver;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovedDocumentReadPolicyTest {

    @Mock
    private PermissionResolver permissionResolver;

    @InjectMocks
    private ApprovedDocumentReadPolicy policy;

    private Document document;
    private OrgNode issuingOrg;
    private UserLogin creator;
    private UserLogin otherUser;

    @BeforeEach
    void setUp() {
        creator = UserLogin.builder().id(100L).username("creator").role(Role.USER).build();
        otherUser = UserLogin.builder().id(200L).username("other").role(Role.USER).build();

        issuingOrg = OrgNode.builder().id(50L).nameVn("Khoa CNTT").build();
        document = Document.builder()
                .id(1L)
                .issuingOrgNode(issuingOrg)
                .creatorUser(creator)
                .build();
    }

    @Nested
    @DisplayName("1. Kiểm tra tham số null và dữ liệu không hợp lệ")
    class NullAndInvalidInputs {

        @Test
        @DisplayName("canRead trả về false khi document hoặc actor là null")
        void canRead_nullInputs_returnsFalse() {
            assertFalse(policy.canRead(null, otherUser));
            assertFalse(policy.canRead(document, null));
            assertFalse(policy.canRead(null, null));
        }

        @Test
        @DisplayName("canRead trả về false khi actor có id null")
        void canRead_actorIdNull_returnsFalse() {
            UserLogin nullIdUser = UserLogin.builder().id(null).role(Role.USER).build();
            assertFalse(policy.canRead(document, nullIdUser));
        }

        @Test
        @DisplayName("assertCanRead ném FORBIDDEN khi document hoặc actor là null")
        void assertCanRead_nullInputs_throwsForbidden() {
            BusinessException ex1 = assertThrows(BusinessException.class, () -> policy.assertCanRead(null, otherUser));
            assertEquals(ErrorCode.FORBIDDEN, ex1.getErrorCode());

            BusinessException ex2 = assertThrows(BusinessException.class, () -> policy.assertCanRead(document, null));
            assertEquals(ErrorCode.FORBIDDEN, ex2.getErrorCode());
        }
    }

    @Nested
    @DisplayName("2. Kiểm tra quyền người tạo văn bản (creatorUser và fallback drafter)")
    class CreatorAccess {

        @Test
        @DisplayName("Người tạo qua creatorUser được phép đọc")
        void canRead_creatorUser_success() {
            assertTrue(policy.canRead(document, creator));
            assertDoesNotThrow(() -> policy.assertCanRead(document, creator));
            verifyNoInteractions(permissionResolver);
        }

        @Test
        @DisplayName("creatorUser null nhưng drafter trùng actor (fallback): được phép đọc")
        void canRead_fallbackToDrafter_success() {
            document.setCreatorUser(null);
            document.setDrafter(creator);

            assertTrue(policy.canRead(document, creator));
            assertDoesNotThrow(() -> policy.assertCanRead(document, creator));
            verifyNoInteractions(permissionResolver);
        }

        @Test
        @DisplayName("creatorUser có giá trị khác actor, dù drafter trùng actor vẫn ưu tiên creatorUser và bị chặn")
        void canRead_creatorUserTakesPrecedenceOverDrafter() {
            UserLogin actualCreator = UserLogin.builder().id(999L).build();
            document.setCreatorUser(actualCreator);
            document.setDrafter(creator);

            assertFalse(policy.canRead(document, creator));
        }
    }

    @Nested
    @DisplayName("3. Kiểm tra vai trò quản trị (ADMIN, SYSTEM_ADMIN)")
    class AdminAccess {

        @Test
        @DisplayName("Người dùng có Role.ADMIN được phép đọc kể cả khi không phải người tạo")
        void canRead_adminRole_success() {
            UserLogin admin = UserLogin.builder().id(888L).username("admin").role(Role.ADMIN).build();
            assertTrue(policy.canRead(document, admin));
            assertDoesNotThrow(() -> policy.assertCanRead(document, admin));
            verifyNoInteractions(permissionResolver);
        }

        @Test
        @DisplayName("Người dùng có Role.SYSTEM_ADMIN được phép đọc")
        void canRead_systemAdminRole_success() {
            UserLogin sysAdmin = UserLogin.builder().id(889L).username("sysadmin").role(Role.SYSTEM_ADMIN).build();
            assertTrue(policy.canRead(document, sysAdmin));
            assertDoesNotThrow(() -> policy.assertCanRead(document, sysAdmin));
            verifyNoInteractions(permissionResolver);
        }

        @Test
        @DisplayName("ADMIN và SYSTEM_ADMIN được phép đọc kể cả khi document thiếu đơn vị ban hành")
        void canRead_adminRole_missingOrg_success() {
            document.setIssuingOrgNode(null);
            document.setIssuingOrg(null);

            UserLogin admin = UserLogin.builder().id(888L).username("admin").role(Role.ADMIN).build();
            assertTrue(policy.canRead(document, admin));
        }
    }

    @Nested
    @DisplayName("4. Kiểm tra đơn vị ban hành bị thiếu")
    class MissingOrgId {

        @Test
        @DisplayName("Người dùng thường bị từ chối khi văn bản thiếu đơn vị ban hành (getOrgId() == null)")
        void canRead_missingOrgId_throwsForbidden() {
            document.setIssuingOrgNode(null);
            document.setIssuingOrg(null);

            assertFalse(policy.canRead(document, otherUser));
            BusinessException ex = assertThrows(BusinessException.class, () -> policy.assertCanRead(document, otherUser));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
            verifyNoInteractions(permissionResolver);
        }
    }

    @Nested
    @DisplayName("5. Kiểm tra 3 quyền nghiệp vụ tại đơn vị ban hành")
    class BusinessPermissions {

        @ParameterizedTest
        @ValueSource(strings = {"DOCUMENT_APPROVE", "DOCUMENT_SIGN_OFFICIAL", "DOCUMENT_DISTRIBUTE"})
        @DisplayName("Người dùng có một trong ba quyền nghiệp vụ tại đơn vị ban hành được phép đọc")
        void canRead_hasAnyOfThreePermissions_success(String permission) {
            lenient().when(permissionResolver.hasPermission(eq(otherUser.getId()), eq(otherUser.getRole()), anyString(), eq(50L)))
                    .thenReturn(false);
            when(permissionResolver.hasPermission(otherUser.getId(), otherUser.getRole(), permission, 50L))
                    .thenReturn(true);

            assertTrue(policy.canRead(document, otherUser));
            assertDoesNotThrow(() -> policy.assertCanRead(document, otherUser));
        }

        @Test
        @DisplayName("Người dùng không có quyền nào trong 3 quyền tại đơn vị ban hành bị từ chối")
        void canRead_lacksAllThreePermissions_throwsForbidden() {
            when(permissionResolver.hasPermission(eq(otherUser.getId()), eq(otherUser.getRole()), anyString(), eq(50L)))
                    .thenReturn(false);

            assertFalse(policy.canRead(document, otherUser));
            BusinessException ex = assertThrows(BusinessException.class, () -> policy.assertCanRead(document, otherUser));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }

        @Test
        @DisplayName("Có quyền ở đơn vị khác (org 99L) nhưng không có ở đơn vị ban hành (50L) bị chặn")
        void canRead_permissionInOtherOrgOnly_throwsForbidden() {
            // Cho phép ở org 99L
            lenient().when(permissionResolver.hasPermission(otherUser.getId(), otherUser.getRole(), "DOCUMENT_APPROVE", 99L))
                    .thenReturn(true);
            // Nhưng tại org văn bản (50L) là false
            when(permissionResolver.hasPermission(eq(otherUser.getId()), eq(otherUser.getRole()), anyString(), eq(50L)))
                    .thenReturn(false);

            assertFalse(policy.canRead(document, otherUser));
            BusinessException ex = assertThrows(BusinessException.class, () -> policy.assertCanRead(document, otherUser));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("6. Role nghiệp vụ tĩnh, người ký, người duyệt PENDING không tự cấp quyền")
    class NonQualifyingAttributes {

        @ParameterizedTest
        @ValueSource(strings = {"LEADER", "CLERK", "REVIEWER"})
        @DisplayName("Role LEADER, CLERK, REVIEWER không tự cấp quyền đọc APPROVED nếu thiếu permission trong DB")
        void canRead_rolesWithoutPermission_throwsForbidden(String roleName) {
            Role role = Role.from(roleName, Role.USER);
            UserLogin roleUser = UserLogin.builder().id(300L).username(roleName.toLowerCase()).role(role).build();

            when(permissionResolver.hasPermission(eq(300L), eq(role), anyString(), eq(50L)))
                    .thenReturn(false);

            assertFalse(policy.canRead(document, roleUser));
            BusinessException ex = assertThrows(BusinessException.class, () -> policy.assertCanRead(document, roleUser));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }

        @Test
        @DisplayName("Người được chỉ định ký (signer) không tự có quyền đọc nếu không có permission hợp lệ")
        void canRead_signerUserWithoutPermission_throwsForbidden() {
            document.setSigner(otherUser);

            when(permissionResolver.hasPermission(eq(otherUser.getId()), eq(otherUser.getRole()), anyString(), eq(50L)))
                    .thenReturn(false);

            assertFalse(policy.canRead(document, otherUser));
        }
    }

    @Nested
    @DisplayName("7. Kiểm thử tích hợp kiêm nhiệm và kế thừa với PermissionResolver")
    class KiêmNhiemAndInheritanceIntegration {

        private PositionAssignmentRepository positionAssignmentRepository;
        private PositionPermissionRepository positionPermissionRepository;
        private PositionDegRepository positionDegRepository;
        private OrgNodeRepository orgNodeRepository;
        private RolePermissionRepository rolePermissionRepository;
        private PermissionResolver realResolver;
        private ApprovedDocumentReadPolicy realPolicy;

        private OrgNode orgKhoaA;
        private OrgNode orgKhoaB;
        private OrgNode orgTruong;
        private OrgNode orgKhoaCon;

        @BeforeEach
        void setUpRealResolver() {
            positionAssignmentRepository = mock(PositionAssignmentRepository.class);
            positionPermissionRepository = mock(PositionPermissionRepository.class);
            positionDegRepository = mock(PositionDegRepository.class);
            orgNodeRepository = mock(OrgNodeRepository.class);
            rolePermissionRepository = mock(RolePermissionRepository.class);

            realResolver = new PermissionResolver(
                    positionAssignmentRepository,
                    positionPermissionRepository,
                    positionDegRepository,
                    orgNodeRepository,
                    rolePermissionRepository
            );
            realPolicy = new ApprovedDocumentReadPolicy(realResolver);

            lenient().when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            lenient().when(positionDegRepository.hasValidDelegation(anyLong(), anyLong(), anyString(), any(Instant.class)))
                    .thenReturn(false);

            orgKhoaA = OrgNode.builder().id(101L).nameVn("Khoa CNTT").build();
            orgKhoaB = OrgNode.builder().id(102L).nameVn("Khoa Dien").build();
            orgTruong = OrgNode.builder().id(10L).nameVn("Ban Giam Hieu").build();
            orgKhoaCon = OrgNode.builder().id(103L).nameVn("Bo mon Mang").build();
        }

        @Test
        @DisplayName("Kiêm nhiệm: Không có quyền ở Khoa A nhưng có quyền DOCUMENT_APPROVE ở Khoa B -> đọc Khoa B được, Khoa A bị chặn")
        void dualPosition_independentPermissions_orderInvariant() {
            Long dualUserId = 777L;
            UserLogin dualUser = UserLogin.builder().id(dualUserId).username("dualUser").role(Role.USER).build();

            Document docKhoaA = Document.builder().id(11L).issuingOrgNode(orgKhoaA).build();
            Document docKhoaB = Document.builder().id(12L).issuingOrgNode(orgKhoaB).build();

            // Bổ nhiệm 1: Phó khoa tại Khoa A (chỉ có DOCUMENT_SIGN_DRAFT, không có DOCUMENT_APPROVE)
            PositionAssignment paA = PositionAssignment.builder()
                    .positionCd("PHO_KHOA")
                    .orgNode(orgKhoaA)
                    .build();

            // Bổ nhiệm 2: Trưởng khoa tại Khoa B (có DOCUMENT_APPROVE)
            PositionAssignment paB = PositionAssignment.builder()
                    .positionCd("TRUONG_KHOA")
                    .orgNode(orgKhoaB)
                    .build();

            // Cấu hình quyền cho 2 vị trí
            PositionPermission ppA = PositionPermission.builder()
                    .positionCd("PHO_KHOA")
                    .permissionCd("DOCUMENT_SIGN_DRAFT")
                    .inheritToChildren(false)
                    .build();
            PositionPermission ppB = PositionPermission.builder()
                    .positionCd("TRUONG_KHOA")
                    .permissionCd("DOCUMENT_APPROVE")
                    .inheritToChildren(false)
                    .build();

            when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse("PHO_KHOA", "DOCUMENT_APPROVE"))
                    .thenReturn(Optional.empty());
            when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse("TRUONG_KHOA", "DOCUMENT_APPROVE"))
                    .thenReturn(Optional.of(ppB));

            // TH 1: Thứ tự danh sách là [paA, paB]
            when(positionAssignmentRepository.findActiveAssignments(eq(dualUserId), any(Instant.class)))
                    .thenReturn(List.of(paA, paB));

            assertTrue(realPolicy.canRead(docKhoaB, dualUser), "Thứ tự [paA, paB]: Phải đọc được văn bản Khoa B");
            assertFalse(realPolicy.canRead(docKhoaA, dualUser), "Thứ tự [paA, paB]: Không được đọc văn bản Khoa A");

            // TH 2: Hoán đổi thứ tự danh sách thành [paB, paA] -> kết quả phải giống hệt
            when(positionAssignmentRepository.findActiveAssignments(eq(dualUserId), any(Instant.class)))
                    .thenReturn(List.of(paB, paA));

            assertTrue(realPolicy.canRead(docKhoaB, dualUser), "Thứ tự [paB, paA]: Phải đọc được văn bản Khoa B");
            assertFalse(realPolicy.canRead(docKhoaA, dualUser), "Thứ tự [paB, paA]: Không được đọc văn bản Khoa A");
        }

        @Test
        @DisplayName("Kế thừa: Vị trí cấp trên có DOCUMENT_APPROVE với inheritToChildren = true cho phép đọc đơn vị con")
        void inheritance_whenInheritToChildrenTrue_allowsChildOrg() {
            Long bossUserId = 888L;
            UserLogin boss = UserLogin.builder().id(bossUserId).username("boss").role(Role.USER).build();
            Document docChild = Document.builder().id(20L).issuingOrgNode(orgKhoaCon).build();

            PositionAssignment paTruong = PositionAssignment.builder()
                    .positionCd("HIEU_TRUONG")
                    .orgNode(orgTruong)
                    .build();

            PositionPermission ppInherit = PositionPermission.builder()
                    .positionCd("HIEU_TRUONG")
                    .permissionCd("DOCUMENT_APPROVE")
                    .inheritToChildren(true)
                    .build();

            when(positionAssignmentRepository.findActiveAssignments(eq(bossUserId), any(Instant.class)))
                    .thenReturn(List.of(paTruong));
            when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse("HIEU_TRUONG", "DOCUMENT_APPROVE"))
                    .thenReturn(Optional.of(ppInherit));
            when(orgNodeRepository.findActiveDescendantIds(10L)).thenReturn(List.of(103L));

            assertTrue(realPolicy.canRead(docChild, boss), "Có quyền kế thừa thì được đọc văn bản đơn vị con");
        }

        @Test
        @DisplayName("Kế thừa: inheritToChildren = false thì không được đọc văn bản tại đơn vị con")
        void inheritance_whenInheritToChildrenFalse_blocksChildOrg() {
            Long bossUserId = 888L;
            UserLogin boss = UserLogin.builder().id(bossUserId).username("boss").role(Role.USER).build();
            Document docChild = Document.builder().id(20L).issuingOrgNode(orgKhoaCon).build();

            PositionAssignment paTruong = PositionAssignment.builder()
                    .positionCd("HIEU_TRUONG")
                    .orgNode(orgTruong)
                    .build();

            PositionPermission ppNoInherit = PositionPermission.builder()
                    .positionCd("HIEU_TRUONG")
                    .permissionCd("DOCUMENT_APPROVE")
                    .inheritToChildren(false)
                    .build();

            when(positionAssignmentRepository.findActiveAssignments(eq(bossUserId), any(Instant.class)))
                    .thenReturn(List.of(paTruong));
            when(positionPermissionRepository.findByPositionCdAndPermissionCdAndDeleteFlgFalse("HIEU_TRUONG", "DOCUMENT_APPROVE"))
                    .thenReturn(Optional.of(ppNoInherit));

            assertFalse(realPolicy.canRead(docChild, boss), "Không có kế thừa thì bị chặn tại đơn vị con");
        }

        @Test
        @DisplayName("Ủy quyền (Delegation): Người dùng có ủy quyền DOCUMENT_DISTRIBUTE tại đơn vị ban hành được phép đọc")
        void delegation_allowsApprovedDocumentAccess() {
            Long delegatedUserId = 666L;
            UserLogin delegatedUser = UserLogin.builder().id(delegatedUserId).username("clerkSub").role(Role.USER).build();
            Document doc = Document.builder().id(30L).issuingOrgNode(orgKhoaA).build();

            when(positionAssignmentRepository.findActiveAssignments(eq(delegatedUserId), any(Instant.class)))
                    .thenReturn(List.of());
            when(positionDegRepository.hasValidDelegation(eq(delegatedUserId), eq(101L), eq("DOCUMENT_DISTRIBUTE"), any(Instant.class)))
                    .thenReturn(true);

            assertTrue(realPolicy.canRead(doc, delegatedUser), "Ủy quyền hợp lệ cho phép đọc văn bản APPROVED");
        }
    }
}

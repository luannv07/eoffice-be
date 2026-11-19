// Kiểm chứng lưu quyền chức vụ và truy vấn khóa trên database H2 riêng.
package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.config.JpaAuditingConfig;
import vn.edu.epu.eoffice_be.dto.request.PositionDraftSignRequest;
import vn.edu.epu.eoffice_be.entity.DictionaryItem;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import vn.edu.epu.eoffice_be.repository.PositionPermissionRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Dùng schema Liquibase của profile test và rollback dữ liệu từng ca.
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({PositionPermissionService.class, JpaAuditingConfig.class})
class PositionPermissionIntegrationTest {
    @Autowired private PositionPermissionService service;
    @Autowired private DictionaryItemRepository dictionaries;
    @Autowired private PositionPermissionRepository permissions;
    @MockBean private DocumentReadPolicy users;

    // Tạo chức vụ riêng để không phụ thuộc quyền seed của chức vụ thật.
    @BeforeEach
    void setUp() {
        when(users.currentUser()).thenReturn(UserLogin.builder().id(10L).role(Role.ADMIN).build());
        dictionaries.saveAndFlush(DictionaryItem.builder().groupCd("POSITION_CD").itemCd("SIGN_TEST")
                .nameVn("Chức vụ kiểm thử").nameEn("Test position").activeFlg(true).build());
    }

    // Gán lại giữ cùng ID và phạm vi quyền được đọc lại đúng từ database.
    @Test
    void assignmentPersistsAndRepeatedCallKeepsOneActiveRow() {
        PositionDraftSignRequest request = new PositionDraftSignRequest();
        request.setInheritToChildren(false);
        var first = service.assignDraftSign("SIGN_TEST", request);
        permissions.flush();

        request.setInheritToChildren(true);
        var second = service.assignDraftSign("SIGN_TEST", request);
        permissions.flush();

        assertEquals(first.getId(), second.getId());
        var stored = permissions.findByPositionCdAndDeleteFlgFalse("SIGN_TEST");
        assertEquals(1, stored.size());
        assertEquals("DOCUMENT_SIGN_DRAFT", stored.getFirst().getPermissionCd());
        assertTrue(stored.getFirst().isInheritToChildren());
    }

    // Không sửa bản ghi lịch sử đã xóa mềm khi cấp lại quyền.
    @Test
    void deletedPermissionRemainsDeletedWhenPermissionIsGrantedAgain() {
        PositionDraftSignRequest request = new PositionDraftSignRequest();
        request.setInheritToChildren(false);
        var first = service.assignDraftSign("SIGN_TEST", request);
        var retired = permissions.findById(first.getId()).orElseThrow();
        retired.setDeleteFlg(true);
        permissions.saveAndFlush(retired);

        var replacement = service.assignDraftSign("SIGN_TEST", request);
        permissions.flush();

        assertNotEquals(first.getId(), replacement.getId());
        assertTrue(permissions.findById(first.getId()).orElseThrow().isDeleteFlg());
        assertEquals(1, permissions.findByPositionCdAndDeleteFlgFalse("SIGN_TEST").size());
    }
}

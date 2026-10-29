package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.epu.eoffice_be.config.AdminSimulationProperties;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DraftUnitResolverTest {
    PositionAssignmentRepository positions;
    OrgNodeRepository orgs;
    AdminSimulationProperties config;
    DraftUnitResolver resolver;
    final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    @BeforeEach void setup() {
        positions=mock(PositionAssignmentRepository.class); orgs=mock(OrgNodeRepository.class);
        var env=new MockEnvironment();env.setActiveProfiles("test"); config=new AdminSimulationProperties(env);
        resolver=new DraftUnitResolver(positions,orgs,config);
    }
    UserLogin user(Role role) { return UserLogin.builder().id(10L).role(role).build(); }
    OrgNode org(long id,String type) {return OrgNode.builder().id(id).typeCd(type).nameVn(type).build();}
    PositionAssignment assignment(OrgNode unit,String cd) {
        return PositionAssignment.builder().id(1L).userLogin(user(Role.USER)).orgNode(unit).positionCd(cd).effectiveFrom(now.minusSeconds(60)).build();
    }
    @ParameterizedTest @ValueSource(strings={"FACULTY","DEPARTMENT"})
    void userMayOnlySelectTheirOwnUnitWithoutRequiringCvOrGv(String type) {
        var unit=org(100,type); var actor=user(Role.USER);
        when(positions.findActiveAssignments(10L,now)).thenReturn(List.of(assignment(unit,"STAFF")));
        when(orgs.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(unit));
        assertEquals(100L,resolver.resolve(actor,100L,now).getId());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->resolver.resolve(actor,200L,now)).getErrorCode());
        assertEquals(1,resolver.options(actor,now).issuingOrgs().size());assertTrue(resolver.options(actor,now).issuingOrgRequired());
    }
    @Test void missingUserUnitHasSpecificMessageButClerkDoesNotNeedOne() {
        when(positions.findActiveAssignments(10L,now)).thenReturn(List.of());
        assertTrue(assertThrows(BusinessException.class,()->resolver.resolve(user(Role.USER),null,now)).getMessage().contains("chưa được phân"));
        assertNull(resolver.resolve(user(Role.CLERK),null,now));
        assertFalse(resolver.options(user(Role.CLERK),now).issuingOrgRequired());
        assertEquals(List.of(),resolver.options(user(Role.CLERK),now).issuingOrgs());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->resolver.resolve(user(Role.CLERK),100L,now)).getErrorCode());
    }
    @ParameterizedTest @ValueSource(strings={"HT","PHT"})
    void schoolLeaderUsesAssignedSchoolEvenWhenRequestOmitsIt(String cd) {
        var school=org(1,"UNIVERSITY");when(positions.findActiveAssignments(10L,now)).thenReturn(List.of(assignment(school,cd)));
        assertEquals(1L,resolver.resolve(user(Role.LEADER),null,now).getId());
        var error=assertThrows(BusinessException.class,()->resolver.resolve(user(Role.LEADER),100L,now));
        assertEquals(ErrorCode.FORBIDDEN,error.getErrorCode());
        assertEquals("Đơn vị được chọn không thuộc đơn vị được phân nhiệm của bạn.",error.getMessage());
    }
    @ParameterizedTest @EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void administrativeRoleAloneNeverGrantsSimulation(Role role) {
        when(positions.findActiveAssignments(10L,now)).thenReturn(List.of());
        assertThrows(BusinessException.class,()->resolver.resolve(user(role),100L,now));
        config.setAdminSimulationEnabled(true);
        var selected=org(200,"DEPARTMENT");when(orgs.findByIdAndDeleteFlgFalse(200L)).thenReturn(Optional.of(selected));
        assertEquals(200L,resolver.resolve(user(role),200L,now).getId());assertNull(resolver.resolve(user(role),null,now));
        when(orgs.findAllByDeleteFlgFalseOrderByOrgOrderAsc()).thenReturn(List.of(selected));
        assertTrue(resolver.options(user(role),now).adminSimulationEnabled());
    }
    @ParameterizedTest @ValueSource(strings={"future","expired","revoked","deleted","unit-deleted"})
    void invalidAssignmentsNeverSupplyAUserUnit(String reason) {
        var unit=org(100,"FACULTY");var p=assignment(unit,"GV");
        switch(reason){case "future"->p.setEffectiveFrom(now.plusSeconds(1));case "expired"->p.setEffectiveTo(now.minusSeconds(1));case "revoked"->p.setActualDismissalDate(now);case "deleted"->p.setDeleteFlg(true);case "unit-deleted"->unit.setDeleteFlg(true);}
        when(positions.findActiveAssignments(10L,now)).thenReturn(List.of(p));
        assertThrows(BusinessException.class,()->resolver.resolve(user(Role.USER),100L,now));verifyNoInteractions(orgs);
    }
    @Test void ambiguousIdentityIsNotResolvedByTakingFirstRow() {
        when(positions.findActiveAssignments(10L,now)).thenReturn(List.of(assignment(org(100,"FACULTY"),"GV"),assignment(org(200,"FACULTY"),"GV")));
        assertTrue(assertThrows(BusinessException.class,()->resolver.resolve(user(Role.USER),100L,now)).getMessage().contains("duy nhất"));
    }
}

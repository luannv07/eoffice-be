package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.request.SubmitApprovalRequest;
import vn.edu.epu.eoffice_be.dto.response.SubmissionOptionsResponse.SelectionMode;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;

import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionRoutingServiceTest {
    @Mock PositionAssignmentRepository positions;
    @Mock UserInfoRepository profiles;
    SubmissionRoutingService routing;
    final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    final List<PositionAssignment> assignments = new ArrayList<>();
    UserLogin sender;
    Document document;
    OrgNode faculty, department, school;

    @BeforeEach
    void setup() {
        routing = new SubmissionRoutingService(positions, profiles, vn.edu.epu.eoffice_be.service.TestDraftUnits.resolver(positions));
        sender = user(10, Role.USER);
        document = Document.builder().id(1L).drafter(sender).status(DocumentStatus.DRAFT).build();
        faculty = org(100, "FACULTY"); department = org(200, "DEPARTMENT"); school = org(1, "UNIVERSITY");
        document.setIssuingOrgNode(faculty);
        lenient().when(positions.findActiveAssignments(anyLong(), any())).thenAnswer(inv -> assignments.stream()
                .filter(p -> p.getUserLogin().getId().equals(inv.getArgument(0))).toList());
        lenient().when(positions.findSubmissionCandidates(anyCollection(), nullable(Long.class), any()))
                .thenAnswer(inv -> assignments.stream().toList());
    }

    UserLogin user(long id, Role role) { return UserLogin.builder().id(id).username("user" + id).role(role).build(); }
    OrgNode org(long id, String type) { return OrgNode.builder().id(id).typeCd(type).nameVn(type + id).build(); }
    PositionAssignment assign(UserLogin user, OrgNode org, String code) {
        var p = PositionAssignment.builder().id((long) assignments.size() + 1).userLogin(user).orgNode(org)
                .positionCd(code).effectiveFrom(now.minusSeconds(60)).build();
        assignments.add(p);
        if (sender.getId().equals(user.getId())) document.setIssuingOrgNode(org);
        return p;
    }
    List<Long> ids() { return routing.options(document, sender, now).routes().stream()
            .flatMap(r -> r.approvers().stream()).map(p -> p.getUserId()).toList(); }
    void error(String text, Runnable call) {
        var ex = assertThrows(BusinessException.class, call::run);
        assertTrue(ex.getMessage().contains(text), ex.getMessage());
    }

    @ParameterizedTest @ValueSource(strings = {"FACULTY", "DEPARTMENT"})
    void ordinaryUserUsesUnitWithoutCvGvAndCannotChooseParentsOrPeers(String type) {
        OrgNode own = "FACULTY".equals(type) ? faculty : department;
        OrgNode peer = org(999, type);
        document.setIssuingOrgNode(peer); // A client-selected document unit is not membership.
        own.setParent(school);
        assign(sender, own, "NHAN_SU_THUONG");
        String lead = "FACULTY".equals(type) ? "TK" : "TP";
        String deputy = "FACULTY".equals(type) ? "PK" : "PP";
        assign(user(20, Role.REVIEWER), own, lead);
        assign(user(21, Role.REVIEWER), own, deputy);
        assign(user(22, Role.REVIEWER), peer, lead);
        assign(user(23, Role.LEADER), school, "HT");
        assign(user(24, Role.REVIEWER), own, "FACULTY".equals(type) ? "TP" : "TK");
        assertEquals(List.of(20L, 21L), ids());
        assertEquals(SelectionMode.SELECT, routing.options(document, sender, now).selectionMode());
        var request = SubmitApprovalRequest.builder().approverUserId(22L).build();
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> routing.resolve(document, sender, request, now)).getErrorCode());
        assertEquals(SubmissionRoute.REVIEW, routing.resolve(document, sender,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), now).route());
    }

    @ParameterizedTest @ValueSource(strings = {"TK", "PK", "TP", "PP", "VT"})
    void managersAndClerkChooseRectorOrViceRector(String code) {
        sender.setRole("VT".equals(code) ? Role.CLERK : Role.REVIEWER);
        assign(sender, faculty, code);
        assign(user(30, Role.LEADER), school, "HT");
        assign(user(31, Role.LEADER), school, "PHT");
        assertEquals(List.of(30L, 31L), ids());
        assertEquals(SelectionMode.SELECT, routing.options(document, sender, now).selectionMode());
        assertEquals(SubmissionRoute.LEADER_APPROVAL, routing.resolve(document, sender,
                SubmitApprovalRequest.builder().approverUserId(31L).build(), now).route());
    }

    @Test void clerkWithoutAssignmentStillHasLeadershipRouteAndAutoSelectsOnlyCandidate() {
        sender.setRole(Role.CLERK); document.setIssuingOrgNode(null);
        assign(user(30, Role.LEADER), school, "HT");
        assertEquals(SelectionMode.AUTO, routing.options(document, sender, now).selectionMode());
        assertEquals(30L, routing.resolve(document, sender, new SubmitApprovalRequest(), now).approverUserId());
    }

    @Test void viceRectorMustUseRectorAndCannotUseSelfOrQueue() {
        sender.setRole(Role.LEADER);
        assign(sender, school, "PHT"); document.setIssuingOrgNode(school);
        assign(user(30, Role.LEADER), school, "HT");
        assertEquals(List.of(30L), ids());
        assertEquals(SelectionMode.AUTO, routing.options(document, sender, now).selectionMode());
        assertEquals(30L, routing.resolve(document, sender, new SubmitApprovalRequest(), now).approverUserId());
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().approverUserId(sender.getId()).build(), now));
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().route(SubmissionRoute.CLERK_QUEUE).build(), now));
    }

    @Test void rectorTargetsSharedQueueWithoutAnyClerkAccount() {
        sender.setRole(Role.LEADER); assign(sender, school, "HT");
        var options = routing.options(document, sender, now);
        assertEquals(SelectionMode.QUEUE, options.selectionMode());
        assertEquals(DocumentStatus.APPROVED, options.routes().getFirst().nextStatus());
        assertTrue(options.routes().getFirst().approvers().isEmpty());
        assertNull(routing.resolve(document, sender, new SubmitApprovalRequest(), now).approverUserId());
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().approverUserId(99L).build(), now));
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "SYSTEM_ADMIN"})
    void adminSelectsReviewersInSelectedUnitOrLeadersAndMustSpecifyRoute(Role role) {
        sender.setRole(role);
        assign(user(20, Role.REVIEWER), faculty, "TK");
        assign(user(21, Role.REVIEWER), department, "PP");
        assign(user(30, Role.LEADER), school, "HT");
        assign(user(31, Role.LEADER), school, "PHT");
        assign(user(40, Role.CLERK), department, "VT");
        assign(user(41, Role.USER), faculty, "GV");
        assertEquals(List.of(20L, 30L, 31L), ids());
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().route(SubmissionRoute.REVIEW).approverUserId(21L).build(), now));
        document.setIssuingOrgNode(department);
        assertEquals(List.of(21L, 30L, 31L), ids());
        document.setIssuingOrgNode(faculty);
        assertEquals(SubmissionRoute.LEADER_APPROVAL, routing.resolve(document, sender,
                SubmitApprovalRequest.builder().route(SubmissionRoute.LEADER_APPROVAL).approverUserId(31L).build(), now).route());
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), now));
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().route(SubmissionRoute.CLERK_QUEUE).approverUserId(40L).build(), now));
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().route(SubmissionRoute.REVIEW).approverUserId(30L).build(), now));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void noRoleCanReadOtherDraftersOptions(Role role) {
        sender.setRole(role); document.setDrafter(user(99, Role.USER));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> routing.options(document, sender, now)).getErrorCode());
        verifyNoInteractions(positions, profiles);
    }

    @ParameterizedTest @ValueSource(strings = {"GĐTT", "GDTT", "PGDTT", "BT", "PBT", "UV"})
    void unsupportedManagementPositionsAreNotOrdinaryUsers(String code) {
        assign(sender, faculty, code);
        error("Chức danh chưa có tuyến", () -> routing.options(document, sender, now));
    }

    @Test void missingUnitAndNonUserMissingPositionHaveDistinctErrors() {
        error("USER chưa được phân", () -> routing.options(document, sender, now));
        sender.setRole(Role.LEADER);
        error("chưa có chức vụ/phân nhiệm", () -> routing.options(document, sender, now));
    }

    @Test void multipleSenderUnitsAreRejectedButMultiUnitReceiverRemainsEligible() {
        assign(sender, faculty, "GV");
        UserLogin dean = user(20, Role.REVIEWER);
        assign(dean, faculty, "TK"); assign(dean, department, "TP"); assign(dean, faculty, "PK");
        assertEquals(List.of(20L), ids());
        assertEquals("PK", routing.options(document, sender, now).routes().getFirst().approvers().getFirst().getPositionCd());
        assign(sender, department, "GV");
        error("Không xác định được duy nhất", () -> routing.options(document, sender, now));
    }

    @ParameterizedTest @ValueSource(strings = {"future", "expired", "dismissed", "deleted", "unitDeleted", "userDeleted", "disabled", "locked", "nullFrom"})
    void invalidReceiverAssignmentsAreNeverReturned(String condition) {
        assign(sender, faculty, "GV");
        var p = assign(user(20, Role.REVIEWER), faculty, "TK");
        switch (condition) {
            case "future" -> p.setEffectiveFrom(now.plusSeconds(1));
            case "expired" -> p.setEffectiveTo(now.minusSeconds(1));
            case "dismissed" -> p.setActualDismissalDate(now);
            case "deleted" -> p.setDeleteFlg(true);
            case "unitDeleted" -> p.setOrgNode(org(100, "FACULTY"));
            case "userDeleted" -> p.getUserLogin().setDeleteFlg(true);
            case "disabled" -> p.getUserLogin().setUserStatusCd("DISABLED");
            case "locked" -> p.getUserLogin().setUserStatusCd("LOCKED");
            case "nullFrom" -> p.setEffectiveFrom(null);
        }
        if (condition.equals("unitDeleted")) p.getOrgNode().setDeleteFlg(true);
        error("Không có trưởng/phó", () -> routing.options(document, sender, now));
    }

    @Test void changedAssignmentAfterOptionsIsReevaluatedAtSubmission() {
        assign(sender, faculty, "GV");
        var dean = assign(user(20, Role.REVIEWER), faculty, "TK");
        assertEquals(List.of(20L), ids());
        dean.setActualDismissalDate(now);
        assertThrows(BusinessException.class, () -> routing.resolve(document, sender,
                SubmitApprovalRequest.builder().approverUserId(20L).build(), now));
    }

    @ParameterizedTest @ValueSource(strings = {"HT", "PHT"})
    void duplicateLeadershipAccountsCauseConfigurationError(String position) {
        sender.setRole(Role.ADMIN);
        assign(user(20, Role.LEADER), school, position);
        assign(user(21, Role.LEADER), school, position);
        error("Có nhiều tài khoản " + position, () -> routing.options(document, sender, now));
    }

    @Test void missingRectorBlocksLeadershipRoutingEvenWithViceRectorPresent() {
        sender.setRole(Role.CLERK); document.setIssuingOrgNode(null); assign(user(30, Role.LEADER), school, "PHT");
        error("Chưa có Hiệu trưởng", () -> routing.options(document, sender, now));
    }

    @ParameterizedTest @ValueSource(strings = {"HT", "TK", "STAFF"})
    void conflictingClerkRoleAndPositionAreRejected(String cd) {
        sender.setRole(Role.CLERK); document.setIssuingOrgNode(null); assign(sender, school, cd);
        error("Không xác định được duy nhất", () -> routing.options(document, sender, now));
    }
}

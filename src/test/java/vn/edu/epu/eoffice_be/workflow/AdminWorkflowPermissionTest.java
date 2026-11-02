package vn.edu.epu.eoffice_be.workflow;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.service.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminWorkflowPermissionTest {
    static Stream<Arguments> forbiddenActions() {
        return Stream.of(Role.ADMIN,Role.SYSTEM_ADMIN).flatMap(role -> Arrays.stream(WorkflowAction.values())
                .filter(action -> !Set.of(WorkflowAction.SUBMIT,WorkflowAction.SUBMIT_TO_LEADER,WorkflowAction.WITHDRAW).contains(action))
                .flatMap(action -> Stream.of(true,false).map(owner -> Arguments.of(role,action,owner))));
    }

    @ParameterizedTest @MethodSource("forbiddenActions")
    void administrativeRoleCannotAcquireBusinessAuthorityEvenWhenPermissionResolverWouldAllowIt(
            Role role,WorkflowAction action,boolean owner) {
        var documents=mock(DocumentRepository.class);
        var logs=mock(DocumentWorkflowLogRepository.class);
        var permissions=mock(PermissionResolver.class, invocation -> {
            if (invocation.getMethod().getName().equals("hasPermission")) return true;
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        var routes=mock(SubmissionRoutingService.class);
        var engine=new WorkflowEngine(documents,logs,permissions,routes);
        var actor=UserLogin.builder().id(10L).role(role).build();
        var doc=Document.builder().id(1L).status(DocumentStatus.IN_REVIEW)
                .drafter(UserLogin.builder().id(owner?10L:20L).build()).signer(actor)
                .issuingOrgNode(OrgNode.builder().id(100L).build()).build();

        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> engine.validateActorPermission(doc,actor,action)).getErrorCode());
        assertEquals(DocumentStatus.IN_REVIEW,doc.getStatus());
        verifyNoInteractions(documents,logs,permissions,routes);
    }
}

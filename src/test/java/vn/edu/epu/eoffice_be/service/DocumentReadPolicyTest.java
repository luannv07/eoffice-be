package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.entity.*;
import vn.edu.epu.eoffice_be.repository.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentReadPolicyTest {
    UserLoginRepository users;
    DocumentApprovalRequestRepository requests;
    DocumentReadPolicy policy;
    @BeforeEach void setup() {
        users = mock(UserLoginRepository.class); requests = mock(DocumentApprovalRequestRepository.class);
        policy = new DocumentReadPolicy(users, requests);
    }
    static Stream<Arguments> subjects() {
        return Arrays.stream(Role.values()).flatMap(r -> DocumentReadPolicy.STATUSES.stream().map(s -> Arguments.of(r,s)));
    }
    UserLogin user(Role role) {
        var user = UserLogin.builder().id(10L).role(role).build();
        when(users.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(user)); return user;
    }
    Document doc(DocumentStatus status) { return Document.builder().id(1L).status(status).drafter(UserLogin.builder().id(20L).build()).build(); }
    @ParameterizedTest @MethodSource("subjects")
    void ownerCanReadInEveryRoleAndGuardedState(Role role, DocumentStatus status) {
        var user = user(role); var doc = doc(status); doc.setDrafter(user);
        assertDoesNotThrow(() -> policy.assertRead(doc, 10L)); verifyNoInteractions(requests);
    }
    @ParameterizedTest @MethodSource("subjects")
    void outsiderCannotReadEvenWhenAdminOrLeader(Role role, DocumentStatus status) {
        user(role); var doc = doc(status);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> policy.assertRead(doc, 10L)).getErrorCode());
        verify(requests).existsParticipant(1L,10L);
    }
    @ParameterizedTest @MethodSource("subjects")
    void historicalParticipantCanReadWithoutGettingWriteRights(Role role, DocumentStatus status) {
        user(role); var doc = doc(status); when(requests.existsParticipant(1L,10L)).thenReturn(true);
        assertDoesNotThrow(() -> policy.assertRead(doc,10L));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> policy.assertDraftWrite(doc,10L)).getErrorCode());
    }
    @ParameterizedTest @EnumSource(value=DocumentStatus.class,names={"PUBLISHED","ISSUED","REVOKED","APPROVED","PENDING_SIGN"})
    void registryAndPublishedRequireTheirLegacyBranch(DocumentStatus status) { assertFalse(policy.applies(doc(status))); }
    @Test void disabledAccountDoesNotRegainAccessThroughHistory() {
        var user = user(Role.ADMIN); user.setUserStatusCd("DISABLED");
        assertEquals(ErrorCode.ACCOUNT_DISABLED, assertThrows(BusinessException.class, () -> policy.assertRead(doc(DocumentStatus.DRAFT),10L)).getErrorCode());
        verifyNoInteractions(requests);
    }
    @Test void missingDrafterIsControlledAndDoesNotGrantAnAdminBypass() {
        user(Role.ADMIN);var document = doc(DocumentStatus.DRAFT); document.setDrafter(null);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> policy.assertRead(document,10L)).getErrorCode());
    }
}

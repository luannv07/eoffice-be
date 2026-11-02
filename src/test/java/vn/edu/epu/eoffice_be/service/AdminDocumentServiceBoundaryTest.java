package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.epu.eoffice_be.common.enums.*;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.dto.PublishRequest;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.*;
import vn.edu.epu.eoffice_be.security.JwtUserPrincipal;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDocumentServiceBoundaryTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void legacyPublishRejectsDatabaseAdminBeforeAnyDocumentStorageOrDistributionAccess(Role role) {
        var users=mock(UserLoginRepository.class);
        when(users.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(UserLogin.builder().id(10L).role(role).build()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(10L,"operator","CLERK",true,List.of()),null,List.of()));
        var policy=new DocumentReadPolicy(users,mock(DocumentApprovalRequestRepository.class));
        var documents=mock(DocumentRepository.class);var distribution=mock(DocumentDistributionRepository.class);
        var book=mock(DocumentBookService.class);var storage=mock(DocumentPdfStorageService.class);var profiles=mock(UserInfoRepository.class);
        var publishing=new DocumentPublishService(documents,distribution,book,storage,profiles,policy);

        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> publishing.publishDocumentResponse(1L,new PublishRequest())).getErrorCode());
        verifyNoInteractions(documents,distribution,book,storage,profiles);
    }

    @ParameterizedTest @EnumSource(value=Role.class,names={"ADMIN","SYSTEM_ADMIN"})
    void staleAdminClaimCannotOpenAnAdminQueryBranch(Role claimedRole) {
        var users=mock(UserLoginRepository.class);
        when(users.findByIdAndDeleteFlgFalse(10L)).thenReturn(Optional.of(UserLogin.builder().id(10L).role(Role.USER).build()));
        var authentication=new UsernamePasswordAuthenticationToken(
                new JwtUserPrincipal(10L,"user",claimedRole.name(),true,List.of()),null,List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var policy=new DocumentReadPolicy(users,mock(DocumentApprovalRequestRepository.class));
        var documents=mock(DocumentRepository.class);var distribution=mock(DocumentDistributionRepository.class);
        var storage=mock(DocumentPdfStorageService.class);var profiles=mock(UserInfoRepository.class);
        var queries=new DocumentQueryService(documents,distribution,storage,profiles,policy);

        assertEquals(Role.USER,queries.resolveViewerRole(authentication));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> queries.getDocumentDetailsForAdmin(1L)).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,
                () -> queries.downloadDocumentForAdmin(1L)).getErrorCode());
        verifyNoInteractions(documents,distribution,storage,profiles);
    }
}

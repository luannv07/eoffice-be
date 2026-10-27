package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.epu.eoffice_be.common.enums.ApprovalRequestStatus;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.UploadProperties;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentApprovalRequest;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentApprovalRequestRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.StoredFileMetadataRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;
import vn.edu.epu.eoffice_be.storage.provider.FileStorageProviderRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentPdfAccessTest {
    enum Kind { PRIMARY, OFFICIAL }
    enum Access { DRAFTER, ADMIN, SYSTEM_ADMIN, PENDING_APPROVER, APPROVED, PUBLISHED }
    @Mock DocumentRepository documents;
    @Mock UserLoginRepository users;
    @Mock DocumentApprovalRequestRepository approvals;
    @Mock DocumentPdfStorageService storage;
    @InjectMocks DocumentAttachmentService service;

    @ParameterizedTest
    @MethodSource("allowedAccess")
    void usesExistingAttachmentPermissionsAndReadsOnlyRequestedVersion(Kind kind, Access access) {
        Document document = prepare();
        UserLogin actor = UserLogin.builder().id(100L).role(Role.USER).build();
        when(users.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(actor));
        switch (access) {
            case DRAFTER -> document.setDrafter(actor);
            case ADMIN -> actor.setRole(Role.ADMIN);
            case SYSTEM_ADMIN -> actor.setRole(Role.SYSTEM_ADMIN);
            case PENDING_APPROVER -> when(approvals.findByDocumentIdAndDeleteFlgFalseOrderBySubmittedAtDesc(5001L))
                    .thenReturn(List.of(DocumentApprovalRequest.builder().approverUser(actor)
                            .status(ApprovalRequestStatus.PENDING).build()));
            case APPROVED -> document.setStatus(DocumentStatus.APPROVED);
            case PUBLISHED -> document.setStatus(DocumentStatus.PUBLISHED);
        }
        Resource pdf = new ByteArrayResource(new byte[]{1, 2, 3});
        when(storage.readAsResource(reference(kind))).thenReturn(pdf);

        assertSame(pdf, download(kind, 100L));
        verify(storage).readAsResource(reference(kind));
        verifyNoMoreInteractions(storage);
    }

    static Stream<Arguments> allowedAccess() {
        return Arrays.stream(Kind.values()).flatMap(kind -> Arrays.stream(Access.values()).map(access -> Arguments.of(kind, access)));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void unrelatedUserIsRejectedBeforeStorageIsRead(Kind kind) {
        prepare();
        when(users.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(UserLogin.builder().id(100L).role(Role.USER).build()));
        BusinessException error = assertThrows(BusinessException.class, () -> download(kind, 100L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void missingUserIsRejectedBeforeStorageIsRead(Kind kind) {
        prepare();
        BusinessException error = assertThrows(BusinessException.class, () -> download(kind, null));
        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        verifyNoInteractions(storage, users);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void missingOrDeletedDocumentIsNotFound(Kind kind) {
        BusinessException error = assertThrows(BusinessException.class, () -> download(kind, 100L));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(storage, users);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void missingRequestedVersionDoesNotServeTheOtherVersion(Kind kind) {
        Document document = prepare();
        document.setDrafter(UserLogin.builder().id(100L).build());
        when(users.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(UserLogin.builder().id(100L).role(Role.USER).build()));
        if (kind == Kind.PRIMARY) document.setPrimaryFilePath(null);
        else document.setOfficialFilePath(null);
        // Exercise the real blank-reference validation, without touching any storage or database.
        var registry = mock(FileStorageProviderRegistry.class);
        var metadata = mock(StoredFileMetadataRepository.class);
        var legacy = mock(FileStorageService.class);
        ReflectionTestUtils.setField(service, "documentPdfStorageService",
                new DocumentPdfStorageService(registry, metadata, legacy, new UploadProperties()));

        BusinessException error = assertThrows(BusinessException.class, () -> download(kind, 100L));
        assertEquals(ErrorCode.DOCUMENT_PDF_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(registry, metadata, legacy);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void providerFailureIsPreserved(Kind kind) {
        Document document = prepare();
        document.setDrafter(UserLogin.builder().id(100L).build());
        when(users.findByIdAndDeleteFlgFalse(100L)).thenReturn(Optional.of(UserLogin.builder().id(100L).role(Role.USER).build()));
        BusinessException failure = new BusinessException(ErrorCode.DOCUMENT_PDF_STORAGE_UNAVAILABLE);
        when(storage.readAsResource(reference(kind))).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class, () -> download(kind, 100L)));
        verifyNoMoreInteractions(storage);
    }

    private Document prepare() {
        Document document = Document.builder().id(5001L).drafter(UserLogin.builder().id(200L).build())
                .status(DocumentStatus.IN_REVIEW).primaryFilePath("storage://35001").officialFilePath("storage://36001").build();
        when(documents.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        return document;
    }

    private String reference(Kind kind) { return kind == Kind.PRIMARY ? "storage://35001" : "storage://36001"; }

    private Resource download(Kind kind, Long userId) {
        return kind == Kind.PRIMARY ? service.downloadPrimaryPdf(5001L, userId) : service.downloadOfficialPdf(5001L, userId);
    }
}

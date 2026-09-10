package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.common.enums.Role;
import vn.edu.epu.eoffice_be.common.enums.WorkflowAction;
import vn.edu.epu.eoffice_be.dto.StampInfo;
import vn.edu.epu.eoffice_be.dto.request.DocumentApproveRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentInitialSignRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentPublishRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRejectRequest;
import vn.edu.epu.eoffice_be.dto.request.DocumentRevisionRequest;
import vn.edu.epu.eoffice_be.dto.response.DocumentDetailResponse;
import vn.edu.epu.eoffice_be.dto.response.DocumentWorkflowLogResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.DocumentWorkflowLog;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.DocumentWorkflowLogRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.service.impl.DocumentWorkflowServiceImpl;
import vn.edu.epu.eoffice_be.workflow.WorkflowEngine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentWorkflowServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentWorkflowLogRepository documentWorkflowLogRepository;
    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private OrgNodeRepository orgNodeRepository;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PdfProcessingService pdfProcessingService;

    @InjectMocks
    private DocumentWorkflowServiceImpl documentWorkflowService;

    private UserLogin leader;
    private OrgNode orgNode;
    private Document document;

    @BeforeEach
    void setUp() {
        leader = UserLogin.builder().id(1002L).username("leader").role(Role.ADMIN).build();
        orgNode = OrgNode.builder().id(2001L).nameVn("Khoa CNTT").build();

        document = Document.builder()
                .id(5001L)
                .title("Du thao Ke hoach 2026")
                .documentTypeCd("KE_HOACH")
                .drafter(leader)
                .issuingOrg(orgNode)
                .status(DocumentStatus.SUBMITTED)
                .build();
    }

    @Test
    @DisplayName("UC12: Duyệt văn bản với Bút phê lãnh đạo và đơn vị chủ trì")
    void approve_success() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(orgNodeRepository.findByIdAndDeleteFlgFalse(2001L)).thenReturn(Optional.of(orgNode));

        DocumentApproveRequest request = DocumentApproveRequest.builder()
                .comment("Dong y thong qua")
                .leadershipInstruction("Giao Khoa CNTT chu tri")
                .assignedOrgNodeId(2001L)
                .build();

        DocumentDetailResponse response = documentWorkflowService.approve(5001L, request, 1002L);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                eq(document),
                eq(WorkflowAction.REVIEW_APPROVE),
                eq(leader),
                eq("Dong y thong qua"),
                eq("Giao Khoa CNTT chu tri"),
                eq(orgNode),
                eq(null)
        );
    }

    @Test
    @DisplayName("UC12: Yêu cầu chỉnh sửa văn bản")
    void requestRevision_success() {
        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));

        DocumentRevisionRequest request = DocumentRevisionRequest.builder()
                .comment("Can bo sung phan phan cong nhiem vu")
                .build();

        DocumentDetailResponse response = documentWorkflowService.requestRevision(5001L, request, 1002L);

        assertNotNull(response);
        verify(workflowEngine).executeTransition(
                eq(document),
                eq(WorkflowAction.REQUEST_REVISION),
                eq(leader),
                eq("Can bo sung phan phan cong nhiem vu"),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    @DisplayName("UC13: Ký nháy văn bản và lưu mã băm SHA-256")
    void initialSign_success() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfProcessingService.applyInitialSignStamp(any(byte[].class), any(StampInfo.class))).thenReturn(mockBytes);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), eq("drafts"))).thenReturn("drafts/stamped.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("mock_sha256_hash");

        DocumentInitialSignRequest request = DocumentInitialSignRequest.builder()
                .comment("Da ra soat the thuc")
                .build();

        DocumentDetailResponse response = documentWorkflowService.initialSign(5001L, request, 1002L);

        assertNotNull(response);
        assertEquals("drafts/stamped.pdf", document.getPrimaryFilePath());
        verify(workflowEngine).executeTransition(
                eq(document),
                eq(WorkflowAction.INITIAL_SIGN),
                eq(leader),
                eq("Da ra soat the thuc"),
                eq(null),
                eq(null),
                eq("mock_sha256_hash")
        );
    }

    @Test
    @DisplayName("UC14: Ký số phát hành PKI X.509 và cấp số hiệu văn bản")
    void signAndPublish_success() {
        document.setPrimaryFilePath("drafts/sample.pdf");
        byte[] mockBytes = new byte[]{1, 2, 3};

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(userLoginRepository.findByIdAndDeleteFlgFalse(1002L)).thenReturn(Optional.of(leader));
        when(fileStorageService.loadFileBytes("drafts/sample.pdf")).thenReturn(mockBytes);
        when(pdfProcessingService.signAndCertifyPdf(any(byte[].class), anyString(), anyString())).thenReturn(mockBytes);
        when(fileStorageService.storeFile(any(byte[].class), anyString(), eq("official"))).thenReturn("official/doc_5001.pdf");
        when(fileStorageService.computeSha256(any(byte[].class))).thenReturn("official_sha256_hash");

        DocumentPublishRequest request = DocumentPublishRequest.builder()
                .officialNumber("125/QD-DHEPU")
                .comment("Ky ban hanh chinh thuc")
                .build();

        DocumentDetailResponse response = documentWorkflowService.signAndPublish(5001L, request, 1002L);

        assertNotNull(response);
        assertEquals("125/QD-DHEPU", document.getOfficialNumber());
        assertEquals("official/doc_5001.pdf", document.getOfficialFilePath());
        assertEquals("official_sha256_hash", document.getOfficialHash());

        verify(workflowEngine).executeTransition(
                eq(document),
                eq(WorkflowAction.SIGN_AND_PUBLISH),
                eq(leader),
                eq("Ky ban hanh chinh thuc"),
                eq(null),
                eq(null),
                eq("official_sha256_hash")
        );
    }

    @Test
    @DisplayName("Lấy lịch sử luồng duyệt theo timeline")
    void getWorkflowHistory_success() {
        DocumentWorkflowLog log = DocumentWorkflowLog.builder()
                .id(1L)
                .document(document)
                .action(WorkflowAction.SUBMIT)
                .actor(leader)
                .fromStatus(DocumentStatus.DRAFT)
                .toStatus(DocumentStatus.SUBMITTED)
                .comment("Gui duyet")
                .build();

        when(documentRepository.findByIdAndDeleteFlgFalse(5001L)).thenReturn(Optional.of(document));
        when(documentWorkflowLogRepository.findByDocumentIdAndDeleteFlgFalseOrderByCreatedDateAsc(5001L))
                .thenReturn(List.of(log));

        List<DocumentWorkflowLogResponse> history = documentWorkflowService.getWorkflowHistory(5001L);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(WorkflowAction.SUBMIT, history.get(0).getAction());
    }
}

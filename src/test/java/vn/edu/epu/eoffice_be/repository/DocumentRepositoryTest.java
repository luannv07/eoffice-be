package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.OrgNode;
import vn.edu.epu.eoffice_be.entity.UserLogin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserLoginRepository userLoginRepository;

    @Autowired
    private OrgNodeRepository orgNodeRepository;

    private UserLogin getOrCreateUser() {
        return userLoginRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("UserLogin not found"));
    }

    private OrgNode getOrCreateOrg() {
        return orgNodeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("OrgNode not found"));
    }

    @Test
    @DisplayName("findByStatusIn(List<DocumentStatus>): Trả về văn bản có status khớp và deleteFlg = false, loại trừ deleteFlg = true")
    void findByStatusIn_shouldFilterByStatusAndExcludeDeleted() {
        UserLogin user = getOrCreateUser();
        OrgNode org = getOrCreateOrg();

        // 1. Tạo văn bản có status PENDING_APPROVAL, deleteFlg = false (khớp)
        Document docActive = Document.builder()
                .title("Doc Active Pending")
                .documentTypeCd("CONG_VAN")
                .drafter(user)
                .issuingOrg(org)
                .status(DocumentStatus.PENDING_APPROVAL)
                .deleteFlg(false)
                .build();
        documentRepository.save(docActive);

        // 2. Tạo văn bản có status IN_REVIEW, deleteFlg = false (khớp)
        Document docReview = Document.builder()
                .title("Doc Active Review")
                .documentTypeCd("CONG_VAN")
                .drafter(user)
                .issuingOrg(org)
                .status(DocumentStatus.IN_REVIEW)
                .deleteFlg(false)
                .build();
        documentRepository.save(docReview);

        // 3. Tạo văn bản có status PENDING_APPROVAL nhưng deleteFlg = true (phải bị loại trừ)
        Document docDeleted = Document.builder()
                .title("Doc Deleted Pending")
                .documentTypeCd("CONG_VAN")
                .drafter(user)
                .issuingOrg(org)
                .status(DocumentStatus.PENDING_APPROVAL)
                .deleteFlg(true)
                .build();
        documentRepository.save(docDeleted);

        // 4. Tạo văn bản có status PUBLISHED (không nằm trong danh sách tìm kiếm)
        Document docPublished = Document.builder()
                .title("Doc Published")
                .documentTypeCd("CONG_VAN")
                .drafter(user)
                .issuingOrg(org)
                .status(DocumentStatus.PUBLISHED)
                .deleteFlg(false)
                .build();
        documentRepository.save(docPublished);

        // Truy vấn với danh sách enum DocumentStatus
        List<Document> result = documentRepository.findByStatusIn(
                List.of(DocumentStatus.PENDING_APPROVAL, DocumentStatus.IN_REVIEW)
        );

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(d -> d.getId().equals(docActive.getId())));
        assertTrue(result.stream().anyMatch(d -> d.getId().equals(docReview.getId())));
        assertFalse(result.stream().anyMatch(d -> d.getId().equals(docDeleted.getId())),
                "Văn bản có deleteFlg = true phải bị loại trừ");
        assertFalse(result.stream().anyMatch(d -> d.getId().equals(docPublished.getId())),
                "Văn bản có status PUBLISHED không được xuất hiện");
    }

    @Test
    @DisplayName("findByStatusIn(List<DocumentStatus>, Pageable): Hỗ trợ phân trang đúng với enum DocumentStatus")
    void findByStatusIn_pageable_shouldPaginateProperly() {
        UserLogin user = getOrCreateUser();
        OrgNode org = getOrCreateOrg();

        for (int i = 0; i < 5; i++) {
            Document doc = Document.builder()
                    .title("Paged Doc " + i)
                    .documentTypeCd("CONG_VAN")
                    .drafter(user)
                    .issuingOrg(org)
                    .status(DocumentStatus.SUBMITTED)
                    .deleteFlg(false)
                    .build();
            documentRepository.save(doc);
        }

        Page<Document> page = documentRepository.findByStatusIn(
                List.of(DocumentStatus.SUBMITTED),
                PageRequest.of(0, 3)
        );

        assertNotNull(page);
        assertTrue(page.getTotalElements() >= 5);
        assertEquals(3, page.getContent().size());
        assertTrue(page.getContent().stream().allMatch(d -> d.getStatus() == DocumentStatus.SUBMITTED));
    }
}

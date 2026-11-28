// Kiểm thử tích hợp ánh xạ các cột audit của BaseEntity trên bảng document_distributions.
package vn.edu.epu.eoffice_be.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.entity.DocumentDistribution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Lớp kiểm thử tích hợp thao tác lưu, cập nhật và đọc bản ghi seed của DocumentDistributionRepository.
@SpringBootTest
@ActiveProfiles("test")
class DocumentDistributionRepositoryIntegrationTest {

    // Tạo URL H2 in-memory riêng theo UUID để cách ly Liquibase với các context test khác.
    private static final String DATABASE_URL = "jdbc:h2:mem:document-distribution-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    // Ghi đè cấu hình datasource trỏ vào cơ sở dữ liệu H2 cô lập của lớp test.
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DocumentDistributionRepository distributionRepository;

    @Autowired
    Environment environment;

    // Dọn ngữ cảnh bảo mật sau mỗi test case để tránh rò rỉ actor giữa các test.
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // Thiết lập actor giả lập vào SecurityContextHolder để JPA Auditing ghi nhận.
    private void authenticateAs(String actor) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(actor, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // Kiểm tra lưu mới DocumentDistribution ghi đầy đủ 4 cột audit khi OSIV tắt.
    @Test
    void saveAndFlushPersistsDocumentDistributionWithAuditColumns() {
        assertEquals("false", environment.getProperty("spring.jpa.open-in-view"));
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long savedId = null;

        try {
            // Lưu bản ghi phân phối mới với actor clerk-publish-test trong transaction riêng.
            authenticateAs("clerk-publish-test");
            savedId = transactionTemplate.execute(status -> {
                DocumentDistribution distribution = DocumentDistribution.builder()
                        .documentId(1L)
                        .targetType("ALL")
                        .targetId(null)
                        .isRead(false)
                        .build();
                DocumentDistribution persisted = distributionRepository.saveAndFlush(distribution);
                return persisted.getId();
            });
            assertNotNull(savedId);

            // Đọc lại bản ghi trong transaction mới để xác nhận dữ liệu đã ghi xuống DB.
            Long targetId = savedId;
            DocumentDistribution reloaded = new TransactionTemplate(transactionManager).execute(
                    status -> distributionRepository.findById(targetId).orElseThrow()
            );

            assertNotNull(reloaded);
            assertEquals("clerk-publish-test", reloaded.getCreatedBy());
            assertEquals("clerk-publish-test", reloaded.getUpdatedBy());
            assertNotNull(reloaded.getCreatedDate());
            assertNotNull(reloaded.getUpdatedDate());
        } finally {
            // Xóa bản ghi kiểm thử sau khi kết thúc để giữ nguyên trạng thái DB.
            if (savedId != null) {
                Long idToDelete = savedId;
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> distributionRepository.deleteById(idToDelete)
                );
            }
        }
    }

    // Kiểm tra cập nhật DocumentDistribution giữ nguyên created_* và cập nhật updated_*.
    @Test
    void updatePersistsUpdatedAuditColumnsAndPreservesCreatedAuditColumns() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long savedId = null;

        try {
            // Tạo bản ghi ban đầu với actor clerk-creator.
            authenticateAs("clerk-creator");
            savedId = transactionTemplate.execute(status -> {
                DocumentDistribution distribution = DocumentDistribution.builder()
                        .documentId(1L)
                        .targetType("ALL")
                        .targetId(null)
                        .isRead(false)
                        .build();
                DocumentDistribution persisted = distributionRepository.saveAndFlush(distribution);
                return persisted.getId();
            });
            assertNotNull(savedId);

            // Đọc lại mốc createdBy và createdDate ban đầu từ DB.
            Long targetId = savedId;
            DocumentDistribution initialSnapshot = new TransactionTemplate(transactionManager).execute(
                    status -> distributionRepository.findById(targetId).orElseThrow()
            );
            assertNotNull(initialSnapshot);
            String initialCreatedBy = initialSnapshot.getCreatedBy();
            Instant initialCreatedDate = initialSnapshot.getCreatedDate();
            assertEquals("clerk-creator", initialCreatedBy);
            assertNotNull(initialCreatedDate);

            // Đổi actor sang reader-user và cập nhật trạng thái đã đọc trong transaction mới.
            authenticateAs("reader-user");
            Instant readTimestamp = Instant.now();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                DocumentDistribution existing = distributionRepository.findById(targetId).orElseThrow();
                existing.setIsRead(true);
                existing.setReadAt(readTimestamp);
                distributionRepository.saveAndFlush(existing);
            });

            // Đọc lại bản ghi trong transaction mới để đối chiếu các cột audit sau cập nhật.
            DocumentDistribution updated = new TransactionTemplate(transactionManager).execute(
                    status -> distributionRepository.findById(targetId).orElseThrow()
            );
            assertNotNull(updated);
            assertTrue(updated.getIsRead());
            assertNotNull(updated.getReadAt());
            assertEquals(initialCreatedBy, updated.getCreatedBy());
            assertEquals(initialCreatedDate, updated.getCreatedDate());
            assertEquals("reader-user", updated.getUpdatedBy());
            assertNotNull(updated.getUpdatedDate());
            assertFalse(updated.getUpdatedDate().isBefore(initialCreatedDate));
        } finally {
            // Xóa bản ghi kiểm thử sau khi kết thúc để tránh ảnh hưởng test khác.
            if (savedId != null) {
                Long idToDelete = savedId;
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> distributionRepository.deleteById(idToDelete)
                );
            }
        }
    }

    // Kiểm tra bản ghi seed id = 1L vẫn đọc được và có created_date đã được backfill.
    @Test
    void existingSeedRowRemainsReadableWithBackfilledCreatedDate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Optional<DocumentDistribution> seedRow = transactionTemplate.execute(
                status -> distributionRepository.findById(1L)
        );

        assertNotNull(seedRow);
        assertTrue(seedRow.isPresent());
        assertNotNull(seedRow.get().getCreatedDate());
    }
}

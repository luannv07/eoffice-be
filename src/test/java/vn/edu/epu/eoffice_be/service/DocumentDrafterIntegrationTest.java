package vn.edu.epu.eoffice_be.service;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.common.enums.DocumentStatus;
import vn.edu.epu.eoffice_be.dto.response.DocumentResponse;
import vn.edu.epu.eoffice_be.entity.Document;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.DocumentBook;
import vn.edu.epu.eoffice_be.repository.DocumentBookRepository;
import vn.edu.epu.eoffice_be.repository.DictionaryItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.env.Environment;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.DocumentRepository;
import vn.edu.epu.eoffice_be.repository.OrgNodeRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;
import vn.edu.epu.eoffice_be.storage.DocumentPdfStorageService;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** No test-wide transaction: exercise response mapping with OSIV disabled. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentDrafterIntegrationTest {
    // @MockBean creates a separate context. Its Liquibase run must not reuse the
    // shared test-profile database while other cached contexts still hold it open.
    private static final String DATABASE_URL = "jdbc:h2:mem:document-drafter-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired PlatformTransactionManager transactions;
    @Autowired DocumentRepository documents;
    @Autowired UserLoginRepository users;
    @Autowired OrgNodeRepository orgs;
    @Autowired DocumentQueryService query;
    @Autowired DocumentPublishService publishing;
    @Autowired UserInfoRepository profiles;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired DocumentBookService bookService;
    @Autowired DocumentBookRepository books;
    @Autowired DictionaryItemRepository dictionary;
    @Autowired Environment environment;
    @MockBean DocumentPdfStorageService pdfStorage;

    private void authenticatePublisher() {
        var operator=new TransactionTemplate(transactions).execute(status -> users.saveAndFlush(
                UserLogin.builder().username("publisher-"+UUID.randomUUID()).passwordHash("test")
                .role(vn.edu.epu.eoffice_be.common.enums.Role.CLERK).build()));
        var principal=new vn.edu.epu.eoffice_be.security.JwtUserPrincipal(operator.getId(),operator.getUsername(),"CLERK",true,List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    @org.junit.jupiter.api.AfterEach
    void clearPublisherAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test
    void assignedPageUsesOneAuthorBatchAndConstantSqlAcrossPageSizes() {
        assertEquals("false", environment.getProperty("spring.jpa.open-in-view"));
        TransactionTemplate tx = new TransactionTemplate(transactions);
        List<Long> ids = new ArrayList<>();
        Long bookId = tx.execute(status -> {
            var org = orgs.findAll().getFirst();
            var type = dictionary.findByGroupCdAndDeleteFlgFalseAndActiveFlgTrueOrderByDisplayOrderAsc("DOCUMENT_TYPE").getFirst();
            var book = books.saveAndFlush(DocumentBook.builder().bookCode("SQL-" + UUID.randomUUID())
                    .bookName("Author batch integration").year(2026).bookLevel(1).documentType(type).orgNode(org)
                    .numberPattern("{NUM}/TEST").build());
            for (int i = 0; i < 9; i++) {
                var author = users.saveAndFlush(UserLogin.builder().username("book-author-" + UUID.randomUUID())
                        .passwordHash("test").build());
                profiles.saveAndFlush(UserInfo.builder().userLogin(author).fullName("Book Author").build());
                ids.add(documents.saveAndFlush(Document.builder().title("Book author SQL test")
                        .draftCode("DT-" + UUID.randomUUID()).drafter(author).documentBook(book)
                        .documentTypeCd(type.getItemCd()).issuingOrgNode(org).issuingOrg(org)
                        .officialNumber(i + "/TEST").status(DocumentStatus.APPROVED).build()).getId());
            }
            return book.getId();
        });
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            var one = bookService.getAssignedDocuments(bookId, null, DocumentStatus.APPROVED, PageRequest.of(0, 1));
            long oneAuthorSql = statistics.getPrepareStatementCount();
            assertEquals(9, one.getTotalElements());
            assertEquals(1, one.getNumberOfElements());
            assertTrue(one.hasNext());
            assertSingleAuthorBatch(statistics);
            assertEquals(0, statistics.getEntityStatistics(UserLogin.class.getName()).getFetchCount());

            statistics.clear();
            // Both pages are full, with another result remaining: both execute the count query.
            var many = bookService.getAssignedDocuments(bookId, null, DocumentStatus.APPROVED, PageRequest.of(0, 8));
            assertEquals(9, many.getTotalElements());
            assertEquals(8, many.getNumberOfElements());
            assertTrue(many.hasNext());
            assertEquals(oneAuthorSql, statistics.getPrepareStatementCount());
            assertSingleAuthorBatch(statistics);
            assertEquals(0, statistics.getEntityStatistics(UserLogin.class.getName()).getFetchCount());
            assertEquals(8, many.stream().map(DocumentResponse::getDrafterId).distinct().count());
            assertEquals(ids.reversed().subList(0, 8), many.stream().map(DocumentResponse::getId).toList());
            for (var response : many) {
                assertTrue(response.getDrafterUsername().startsWith("book-author-"));
                assertEquals("Book Author", response.getDrafterFullName());
                assertEquals(bookId, response.getDocumentBookId());
            }
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
            tx.executeWithoutResult(status -> ids.forEach(documents::deleteById));
        }
    }

    private static void assertSingleAuthorBatch(Statistics statistics) {
        var authorQueries = Arrays.stream(statistics.getQueries())
                .filter(q -> q.contains("FROM UserLogin u LEFT JOIN UserInfo i")).toList();
        assertEquals(1, authorQueries.size());
        assertEquals(1, statistics.getQueryStatistics(authorQueries.getFirst()).getExecutionCount());
    }

    @Test
    void authorProjectionLeftJoinsActiveProfilesAndKeepsHistoricalAccounts() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        List<Long> ids = tx.execute(status -> {
            List<Long> result = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UserLogin user = users.saveAndFlush(UserLogin.builder().username("batch-" + UUID.randomUUID())
                        .passwordHash("test").deleteFlg(i == 3).userStatusCd(i == 3 ? "INACTIVE" : "ACTIVE").build());
                result.add(user.getId());
                if (i != 1) {
                    profiles.saveAndFlush(UserInfo.builder().userLogin(user).fullName(i == 3 ? "" : "Name " + i)
                            .deleteFlg(i == 2).build());
                }
            }
            return result;
        });
        var names = profiles.findDrafterNamesByUserIds(ids).stream()
                .collect(Collectors.toMap(UserInfoRepository.DrafterName::getUserId, n -> n));
        assertEquals(4, names.size());
        assertEquals("Name 0", names.get(ids.get(0)).getFullName());
        assertNull(names.get(ids.get(1)).getFullName());
        assertNull(names.get(ids.get(2)).getFullName());
        assertEquals("", names.get(ids.get(3)).getFullName());
        for (Long id : ids) {
            assertEquals(users.findById(id).orElseThrow().getUsername(), names.get(id).getUsername());
        }
    }

    @Test
    void listAuthorSqlRemainsConstantAsDistinctAuthorsAndDocumentsIncrease() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        List<Long> ids = new ArrayList<>();
        try {
            ids.add(createPublishedAuthorDocument(tx));
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            var first = query.getAllPublishedDocuments();
            long oneAuthorStatements = statistics.getPrepareStatementCount();
            assertEquals(2, oneAuthorStatements, "one document SELECT and one author batch SELECT");
            assertEquals(0, statistics.getEntityFetchCount(), "must not initialize lazy author proxies");
            assertTrue(first.stream().anyMatch(d -> ids.contains(d.getId())));

            for (int i = 0; i < 7; i++) ids.add(createPublishedAuthorDocument(tx));
            statistics.clear();
            // No surrounding transaction: each call opens a new persistence context with OSIV disabled.
            var many = query.getAllPublishedDocuments();
            assertEquals(oneAuthorStatements, statistics.getPrepareStatementCount());
            assertEquals(0, statistics.getEntityFetchCount());
            var created = many.stream().filter(d -> ids.contains(d.getId())).toList();
            assertEquals(8, created.size());
            assertEquals(8, created.stream().map(DocumentResponse::getDrafterId).distinct().count());
            for (var response : created) {
                assertTrue(response.getDrafterUsername().startsWith("sql-author-"));
                assertEquals("SQL Author", response.getDrafterFullName());
                assertNull(response.getDocumentBookId());
            }
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
            tx.executeWithoutResult(status -> ids.forEach(documents::deleteById));
        }
    }

    private Long createPublishedAuthorDocument(TransactionTemplate tx) {
        return tx.execute(status -> {
            UserLogin author = users.saveAndFlush(UserLogin.builder().username("sql-author-" + UUID.randomUUID())
                    .passwordHash("test").build());
            profiles.saveAndFlush(UserInfo.builder().userLogin(author).fullName("SQL Author").build());
            var org = orgs.findAll().getFirst();
            return documents.saveAndFlush(Document.builder().drafter(author).issuingOrg(org).issuingOrgNode(org)
                    .draftCode("DT-" + UUID.randomUUID()).title("SQL author batch test")
                    .documentTypeCd("CONG_VAN").status(DocumentStatus.PUBLISHED).build()).getId();
        });
    }

    @Test
    void publishResponseResolvesAuthorInsideServiceTransactionWithOsivDisabled() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> {
            var user = users.findAll().getFirst();
            var org = orgs.findAll().getFirst();
            return documents.saveAndFlush(Document.builder().drafter(user).issuingOrg(org).issuingOrgNode(org)
                    .draftCode("DT-" + UUID.randomUUID()).title("Publish response integration test")
                    .summary("Test summary").documentTypeCd("CONG_VAN").status(DocumentStatus.APPROVED)
                    .officialFilePath("test-response.pdf").build()).getId();
        });
        try {
            when(pdfStorage.readPdf("test-response.pdf")).thenReturn(new byte[]{1, 2, 3});
            authenticatePublisher();
            DocumentResponse response = publishing.publishDocumentResponse(id, null);
            assertEquals(id, response.getId());
            assertEquals(DocumentStatus.PUBLISHED, response.getStatus());
            assertNotNull(response.getDrafterId());
            assertNotNull(response.getDrafterUsername());
            assertNotNull(response.getDrafterFullName());
            var reader=tx.execute(status -> users.saveAndFlush(UserLogin.builder().username("reader-"+UUID.randomUUID())
                    .passwordHash("test").role(vn.edu.epu.eoffice_be.common.enums.Role.ADMIN).build()));
            var readerPrincipal=new vn.edu.epu.eoffice_be.security.JwtUserPrincipal(reader.getId(),reader.getUsername(),"ADMIN",true,List.of());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(readerPrincipal,null,List.of()));
            Map<?, ?> detail = (Map<?, ?>) query.getDocumentDetailsForAdmin(id);
            assertEquals(detail.get("drafterId"), response.getDrafterId());
            assertEquals(detail.get("drafterUsername"), response.getDrafterUsername());
            assertEquals(detail.get("drafterFullName"), response.getDrafterFullName());
        } finally {
            tx.executeWithoutResult(status -> documents.deleteById(id));
        }
    }

    @Test
    void authorResponsesRemainReadableOutsideRepositoryTransactions() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> {
            var user = users.findAll().getFirst();
            var org = orgs.findAll().getFirst();
            return documents.saveAndFlush(Document.builder().drafter(user).issuingOrg(org).issuingOrgNode(org)
                    .draftCode("DT-" + UUID.randomUUID()).title("Author integration test")
                    .summary("Test summary").documentTypeCd("CONG_VAN").status(DocumentStatus.APPROVED)
                    .officialFilePath("test-author.pdf").build()).getId();
        });
        try {
            when(pdfStorage.readPdf("test-author.pdf")).thenReturn(new byte[]{1, 2, 3});
            authenticatePublisher();
            Document published = publishing.publishDocument(id, null);
            assertTrue(Hibernate.isInitialized(published.getDrafter()));
            assertNotNull(published.getDrafter().getUsername());
            var reader=tx.execute(status -> users.saveAndFlush(UserLogin.builder().username("reader-"+UUID.randomUUID())
                    .passwordHash("test").role(vn.edu.epu.eoffice_be.common.enums.Role.ADMIN).build()));
            var readerPrincipal=new vn.edu.epu.eoffice_be.security.JwtUserPrincipal(reader.getId(),reader.getUsername(),"ADMIN",true,List.of());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(readerPrincipal,null,List.of()));
            Map<?, ?> detail = (Map<?, ?>) query.getDocumentDetailsForAdmin(id);
            assertEquals(published.getDrafter().getId(), detail.get("drafterId"));
            assertEquals(published.getDrafter().getUsername(), detail.get("drafterUsername"));
            assertNotNull(detail.get("drafterFullName"));
            DocumentResponse response = query.getAllPublishedDocuments().stream()
                    .filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
            assertEquals(published.getDrafter().getId(), response.getDrafterId());
            assertEquals(published.getDrafter().getUsername(), response.getDrafterUsername());
            assertNotNull(response.getDrafterFullName());
        } finally {
            tx.executeWithoutResult(status -> documents.deleteById(id));
        }
    }
}

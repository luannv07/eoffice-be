package vn.edu.epu.eoffice_be.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the recovery queries, including deleted and legacy audit rows, against SQL. */
class PkiSigningHistoryTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private PkiSigningHistory history;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE document_workflow_logs (actor_id BIGINT, signature_hash VARCHAR(64), delete_flg BOOLEAN)");
        jdbc.execute("CREATE TABLE documents (signer_id BIGINT, official_hash VARCHAR(64), official_file_path VARCHAR(500), primary_file_path VARCHAR(500), delete_flg BOOLEAN)");
        jdbc.execute("CREATE TABLE document_process_logs (actor_user_id BIGINT, action VARCHAR(50), note VARCHAR(2000), delete_flg BOOLEAN)");
        history = new PkiSigningHistory(jdbc);
    }

    @AfterEach
    void tearDown() { database.shutdown(); }

    @ParameterizedTest
    @ValueSource(strings = {"workflow", "official-hash", "official-file", "legacy"})
    void deletedHistoryStillRequiresOriginalKeys(String source) {
        switch (source) {
            case "workflow" -> jdbc.update("INSERT INTO document_workflow_logs VALUES (?, ?, true)", 1001L, "a".repeat(64));
            case "official-hash" -> jdbc.update("INSERT INTO documents(signer_id, official_hash, delete_flg) VALUES (?, ?, true)", 1001L, "b".repeat(64));
            case "official-file" -> jdbc.update("INSERT INTO documents(signer_id, official_file_path, delete_flg) VALUES (?, ?, true)", 1001L, "official/signed.pdf");
            case "legacy" -> jdbc.update("INSERT INTO document_process_logs VALUES (?, 'SUBMIT_APPROVAL', ?, true)", 1001L,
                    "Gửi văn bản tới cán bộ duyệt [Đã ký nháy tự động, SHA256: " + "c".repeat(64) + "]");
            default -> fail("Unknown test case");
        }

        assertTrue(history.hasAnySigningHistory());
        assertTrue(history.hasUserSigningHistory("1001"));
        assertFalse(history.hasUserSigningHistory("1002"));
    }

    @Test
    void ordinaryDraftsAndBlankHashesAreNotSigningEvidence() {
        jdbc.update("INSERT INTO documents(signer_id, primary_file_path, official_hash, official_file_path, delete_flg) VALUES (?, ?, '  ', '', false)",
                1001L, "sample.pdf");
        jdbc.update("INSERT INTO document_workflow_logs VALUES (?, ' ', false)", 1001L);
        jdbc.update("INSERT INTO document_process_logs VALUES (?, 'SUBMIT_APPROVAL', ?, false)", 1001L, "Gửi văn bản tới cán bộ duyệt");
        assertFalse(history.hasAnySigningHistory());
        assertFalse(history.hasUserSigningHistory("1001"));
    }

    @Test
    void malformedUserIdentifierIsRejectedBeforeQuery() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> history.hasUserSigningHistory("1001 OR 1=1"));
        assertEquals(ErrorCode.PKI_USER_KEYSTORE_INVALID, error.getErrorCode());
    }
}

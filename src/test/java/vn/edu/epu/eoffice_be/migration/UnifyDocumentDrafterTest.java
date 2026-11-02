package vn.edu.epu.eoffice_be.migration;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnifyDocumentDrafterTest {
    private Connection fixture() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE user_logins(id BIGINT PRIMARY KEY, delete_flg BOOLEAN)");
            s.execute("INSERT INTO user_logins VALUES (1,FALSE),(2,FALSE),(3,TRUE)");
            s.execute("""
                    CREATE TABLE documents(id BIGINT PRIMARY KEY, drafter_id BIGINT, creator_user_id BIGINT,
                    status VARCHAR(30) DEFAULT 'DRAFT', delete_flg BOOLEAN DEFAULT FALSE,
                    submitted_at TIMESTAMP, primary_file_path VARCHAR(200), official_file_path VARCHAR(200),
                    official_number VARCHAR(100), official_hash VARCHAR(100), parent_document_id BIGINT)
                    """);
            s.execute("CREATE INDEX idx_creator_test ON documents(creator_user_id)");
            s.execute("CREATE TABLE document_versions(id BIGINT PRIMARY KEY, document_id BIGINT REFERENCES documents(id))");
            s.execute("CREATE TABLE document_attachments(id BIGINT PRIMARY KEY, document_id BIGINT REFERENCES documents(id),"
                    + " document_version_id BIGINT REFERENCES document_versions(id))");
            s.execute("CREATE TABLE document_process_logs(document_id BIGINT REFERENCES documents(id), action VARCHAR(50))");
            for (String table : new String[]{"document_approval_requests", "document_workflow_logs", "document_distributions"}) {
                s.execute("CREATE TABLE " + table + "(document_id BIGINT REFERENCES documents(id))");
            }
        }
        c.setAutoCommit(false);
        return c;
    }

    private Database database(Connection c) throws Exception {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
    }

    private long value(Connection c, String query) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(query)) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }

    @Test
    void migrationKeepsDrafterCopiesMissingAndPreservesHistoricalUsers() throws Exception {
        try (Connection c = fixture(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE documents ADD CONSTRAINT fk_creator_test FOREIGN KEY(creator_user_id) REFERENCES user_logins(id)");
            s.execute("INSERT INTO documents(id,drafter_id,creator_user_id) VALUES (10,1,1),(11,1,2),(12,NULL,2),(13,3,NULL),(14,999,1)");
            s.execute("INSERT INTO documents(id,drafter_id,creator_user_id,delete_flg) VALUES (15,NULL,3,TRUE)");
            try (Liquibase migration = new Liquibase("db/changelog/changes/2026-10-27_unify-document-drafter.yml",
                    new ClassLoaderResourceAccessor(), database(c))) {
                migration.update(new liquibase.Contexts(), new liquibase.LabelExpression());
                assertEquals(1, value(c, "SELECT drafter_id FROM documents WHERE id=11"));
                assertEquals(2, value(c, "SELECT drafter_id FROM documents WHERE id=12"));
                assertEquals(3, value(c, "SELECT drafter_id FROM documents WHERE id=13"));
                assertEquals(1, value(c, "SELECT drafter_id FROM documents WHERE id=14"));
                assertEquals(3, value(c, "SELECT drafter_id FROM documents WHERE id=15"));
                assertThrows(Exception.class, () -> s.executeQuery("SELECT creator_user_id FROM documents"));
                assertThrows(Exception.class, () -> s.execute("INSERT INTO documents(id) VALUES(90)"));
                assertThrows(Exception.class, () -> s.execute("INSERT INTO documents(id,drafter_id) VALUES(91,999)"));
                migration.update(new liquibase.Contexts(), new liquibase.LabelExpression());
                assertEquals(6, value(c, "SELECT COUNT(*) FROM documents"));
            }
        }
    }

    @Test
    void deletesOnlyUnusedDraftAndItsRelationalChildren() throws Exception {
        try (Connection c = fixture(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO documents(id,drafter_id,creator_user_id) VALUES(20,NULL,NULL),(21,1,NULL)");
            s.execute("INSERT INTO document_versions VALUES(30,20),(31,21)");
            s.execute("INSERT INTO document_attachments VALUES(40,20,30),(41,21,31)");
            s.execute("INSERT INTO document_process_logs VALUES(20,'CREATE_DRAFT'),(20,'UPLOAD_ATTACHMENT')");
            new UnifyDocumentDrafter().execute(database(c));
            assertEquals(1, value(c, "SELECT COUNT(*) FROM documents"));
            assertEquals(21, value(c, "SELECT id FROM documents"));
            assertEquals(1, value(c, "SELECT COUNT(*) FROM document_versions"));
            assertEquals(1, value(c, "SELECT COUNT(*) FROM document_attachments"));
            assertEquals(0, value(c, "SELECT COUNT(*) FROM document_process_logs"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE documents SET status='PUBLISHED' WHERE id=20",
            "UPDATE documents SET submitted_at=CURRENT_TIMESTAMP WHERE id=20",
            "UPDATE documents SET primary_file_path='signed.pdf' WHERE id=20",
            "UPDATE documents SET official_file_path='official.pdf' WHERE id=20",
            "UPDATE documents SET official_number='1/QD' WHERE id=20",
            "UPDATE documents SET official_hash='hash' WHERE id=20",
            "INSERT INTO document_approval_requests VALUES(20)",
            "INSERT INTO document_workflow_logs VALUES(20)",
            "INSERT INTO document_distributions VALUES(20)",
            "INSERT INTO document_process_logs VALUES(20,'SUBMIT_APPROVAL')",
            "INSERT INTO documents(id,drafter_id,parent_document_id) VALUES(99,1,20)",
            "INSERT INTO document_versions VALUES(30,20); INSERT INTO document_attachments VALUES(40,21,30)"
    })
    void blockedRowsReportIdsBeforeAnyMutation(String evidence) throws Exception {
        try (Connection c = fixture(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO documents(id,drafter_id,creator_user_id) VALUES(20,NULL,NULL),(21,NULL,1),(22,NULL,NULL)");
            for (String command : evidence.split(";")) s.execute(command);
            CustomChangeException error = assertThrows(CustomChangeException.class,
                    () -> new UnifyDocumentDrafter().execute(database(c)));
            assertTrue(error.getMessage().contains("20"));
            assertEquals(1, value(c, "SELECT COUNT(*) FROM documents WHERE id=22"));
            assertEquals(1, value(c, "SELECT COUNT(*) FROM documents WHERE id=21 AND drafter_id IS NULL"));
            assertEquals(1, value(c, "SELECT creator_user_id FROM documents WHERE id=21"));
        }
    }
}

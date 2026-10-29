package vn.edu.epu.eoffice_be.migration;

import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminSimulationMigrationTest {
    @Test void upgradePreservesExistingDocumentsAndDefaultsNewRowsToRealDocuments() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE documents(id BIGINT PRIMARY KEY, title VARCHAR(100))");
            statement.execute("INSERT INTO documents VALUES(1,'Historical')");
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration = new Liquibase("db/changelog/changes/2026-10-29_draft-admin-simulation.yml",
                    new ClassLoaderResourceAccessor(), database)) {
                migration.update(new Contexts(),new LabelExpression());
                statement.execute("INSERT INTO documents(id,title) VALUES(2,'New')");
                try (var rows = statement.executeQuery("SELECT id,title,is_test_document FROM documents ORDER BY id")) {
                    assertTrue(rows.next()); assertEquals(1L,rows.getLong(1)); assertEquals("Historical",rows.getString(2)); assertFalse(rows.getBoolean(3));
                    assertTrue(rows.next()); assertEquals(2L,rows.getLong(1)); assertFalse(rows.getBoolean(3)); assertFalse(rows.next());
                }
                assertThrows(SQLException.class,()->statement.execute("INSERT INTO documents(id,is_test_document) VALUES(3,NULL)"));
                migration.update(new Contexts(),new LabelExpression());
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM documents")) { assertTrue(rows.next()); assertEquals(2,rows.getInt(1)); }
            }
        }
    }
}

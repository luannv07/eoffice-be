package vn.edu.epu.eoffice_be.config;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PkiStorageMigrationTest {
    private static final String CHANGELOG = "db/changelog/changes/2026-10-20_add-pki-storage-tables.yml";
    private static final String SIMPLIFY = "db/changelog/changes/2026-10-20_simplify-pki-storage-tables.yml";

    @Test
    void simplificationKeepsOnlyExpectedColumnsAndPreservesBusinessData() throws Exception {
        try (Connection connection = newDatabase()) {
            createExistingData(connection);
            {
                Liquibase initial = migration(connection, CHANGELOG);
                initial.update(new Contexts());
                try (Liquibase simplify = migration(connection, SIMPLIFY)) {
                    simplify.update(new Contexts());
                    simplify.update(new Contexts());
                    assertColumns(connection, "pki_ca", "id", "status", "certificate_pem",
                            "keystore_encrypted", "encryption_nonce", "encryption_key_id",
                            "fingerprint_sha256", "created_at");
                    assertColumns(connection, "pki_user_keystores", "id", "user_id", "ca_id",
                            "certificate_pem", "certificate_serial", "keystore_encrypted",
                            "encryption_nonce", "encryption_key_id", "created_at");
                    assertThrows(SQLException.class, () -> count(connection, "pki_revocations"));
                    assertEquals(1, count(connection, "existing_documents"));
                    assertEquals(1, count(connection, "user_logins"));
                }
            }
        }
    }

    @Test
    void simplificationHaltsWhenPkiContainsData() throws Exception {
        try (Connection connection = newDatabase()) {
            createExistingData(connection);
            {
                Liquibase initial = migration(connection, CHANGELOG);
                initial.update(new Contexts());
                insertCa(connection, "ACTIVE", new byte[12], new byte[17], 1);
                try (Liquibase simplify = migration(connection, SIMPLIFY)) {
                    assertThrows(liquibase.exception.LiquibaseException.class,
                            () -> simplify.update(new Contexts()));
                    assertEquals(1, count(connection, "pki_ca"));
                    assertEquals(0, count(connection, "pki_revocations"));
                }
            }
        }
    }

    private void assertColumns(Connection connection, String table, String... expected) throws SQLException {
        var actual = new java.util.HashSet<String>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            var metadata = rows.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                actual.add(metadata.getColumnName(i).toLowerCase(java.util.Locale.ROOT));
            }
        }
        assertEquals(java.util.Set.of(expected), actual);
    }

    @Test
    void migrationAddsEmptyTablesWithoutChangingExistingBusinessDataAndCanRunTwice() throws Exception {
        try (Connection connection = newDatabase()) {
            createExistingData(connection);
            try (Liquibase liquibase = migration(connection, CHANGELOG)) {
                liquibase.update(new Contexts());
                liquibase.update(new Contexts());

                assertEquals(0, count(connection, "pki_ca"));
                assertEquals(0, count(connection, "pki_user_keystores"));
                assertEquals(0, count(connection, "pki_revocations"));
                assertEquals(1, count(connection, "user_logins"));
                assertEquals(1, count(connection, "existing_documents"));
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT label FROM existing_documents WHERE id = 7")) {
                    assertTrue(rows.next());
                    assertEquals("keep-existing-document", rows.getString(1));
                }
                assertEquals(1, count(connection, "databasechangelog"));
            }
        }
    }

    @Test
    void migrationRejectsInvalidEncryptionShapeAndCertificateDates() throws Exception {
        try (Connection connection = newDatabase()) {
            createExistingData(connection);
            try (Liquibase liquibase = migration(connection, CHANGELOG)) {
                liquibase.update(new Contexts());

                assertThrows(SQLException.class, () -> insertCa(connection, "ACTIVE", new byte[11], new byte[17], 1));
                assertThrows(SQLException.class, () -> insertCa(connection, "ACTIVE", new byte[12], new byte[16], 1));
                assertThrows(SQLException.class, () -> insertCa(connection, "ACTIVE", new byte[12], new byte[17], 0));
                assertThrows(SQLException.class, () -> insertCa(connection, "UNKNOWN", new byte[12], new byte[17], 1));
                insertCa(connection, "ACTIVE", new byte[12], new byte[17], 1);
                assertThrows(SQLException.class, () -> execute(connection,
                        "UPDATE pki_ca SET not_after = not_before WHERE id = 1"));
                assertEquals(1, count(connection, "pki_ca"));
            }
        }
    }

    @Test
    void migrationEnforcesUserAndCaReferencesAndUniqueKeystoresAndRevocations() throws Exception {
        try (Connection connection = newDatabase()) {
            createExistingData(connection);
            try (Liquibase liquibase = migration(connection, CHANGELOG)) {
                liquibase.update(new Contexts());
                insertCa(connection, "ACTIVE", new byte[12], new byte[17], 1);

                assertThrows(SQLException.class, () -> insertUserKeystore(connection, 999, 1));
                assertThrows(SQLException.class, () -> insertUserKeystore(connection, 1001, 999));
                insertUserKeystore(connection, 1001, 1);
                assertThrows(SQLException.class, () -> insertUserKeystore(connection, 1001, 1));
                execute(connection, "INSERT INTO pki_revocations (ca_id, certificate_serial, revoked_by) VALUES (1, 'a1', 1001)");
                assertThrows(SQLException.class, () -> execute(connection,
                        "INSERT INTO pki_revocations (ca_id, certificate_serial) VALUES (1, 'a1')"));
                assertThrows(SQLException.class, () -> execute(connection,
                        "INSERT INTO pki_revocations (ca_id, certificate_serial) VALUES (999, 'b2')"));
                assertThrows(SQLException.class, () -> execute(connection, "DELETE FROM pki_ca WHERE id = 1"));
                assertThrows(SQLException.class, () -> execute(connection, "DELETE FROM user_logins WHERE id = 1001"));
                assertEquals(1, count(connection, "pki_user_keystores"));
                assertEquals(1, count(connection, "pki_revocations"));
            }
        }
    }

    @Test
    void partialExistingPkiSchemaHaltsInsteadOfSilentlySkippingMissingTables() throws Exception {
        try (Connection connection = newDatabase()) {
            createExistingData(connection);
            execute(connection, "CREATE TABLE pki_ca (id BIGINT PRIMARY KEY)");
            execute(connection, "INSERT INTO pki_ca VALUES (19)");
            try (Liquibase liquibase = migration(connection, CHANGELOG)) {
                assertThrows(liquibase.exception.LiquibaseException.class, () -> liquibase.update(new Contexts()));
                assertEquals(1, count(connection, "pki_ca"));
                assertThrows(SQLException.class, () -> count(connection, "pki_user_keystores"));
            }
        }
    }

    @Test
    void fullTestChangelogIncludesThePkiTables() throws Exception {
        try (Connection connection = newDatabase();
             Liquibase liquibase = migration(connection, "db/changelog/master-test.yml")) {
            liquibase.update(new Contexts());
            assertEquals(0, count(connection, "pki_ca"));
            assertEquals(0, count(connection, "pki_user_keystores"));
            assertThrows(SQLException.class, () -> count(connection, "pki_revocations"));
            assertTrue(count(connection, "user_logins") > 0);
        }
    }

    private Connection newDatabase() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:pki-schema-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DEFAULT_NULL_ORDERING=HIGH", "sa", "");
    }

    private Liquibase migration(Connection connection, String changelog) throws Exception {
        return new Liquibase(changelog, new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
    }

    private void createExistingData(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE user_logins (id BIGINT PRIMARY KEY, username VARCHAR(100))");
        execute(connection, "INSERT INTO user_logins VALUES (1001, 'existing-user')");
        execute(connection, "CREATE TABLE existing_documents (id BIGINT PRIMARY KEY, label VARCHAR(100))");
        execute(connection, "INSERT INTO existing_documents VALUES (7, 'keep-existing-document')");
    }

    private void insertCa(Connection connection, String status, byte[] nonce, byte[] ciphertext, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pki_ca (id, status, certificate_pem, keystore_encrypted, encryption_nonce,
                    encryption_key_id, encryption_version, fingerprint_sha256, not_before, not_after)
                VALUES (1, ?, 'test-public-certificate', ?, ?, 'test-key', ?, ?,
                    TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00',
                    TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00')
                """)) {
            statement.setString(1, status);
            statement.setBytes(2, ciphertext);
            statement.setBytes(3, nonce);
            statement.setInt(4, version);
            statement.setString(5, "a".repeat(64));
            statement.executeUpdate();
        }
    }

    private void insertUserKeystore(Connection connection, long userId, long caId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pki_user_keystores (user_id, ca_id, certificate_pem, certificate_serial,
                    keystore_encrypted, encryption_nonce, encryption_key_id, not_before, not_after)
                VALUES (?, ?, 'test-public-certificate', 'a1', ?, ?, 'test-key',
                    TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00',
                    TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00')
                """)) {
            statement.setLong(1, userId);
            statement.setLong(2, caId);
            statement.setBytes(3, new byte[17]);
            statement.setBytes(4, new byte[12]);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}

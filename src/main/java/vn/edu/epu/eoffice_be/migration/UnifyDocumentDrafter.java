package vn.edu.epu.eoffice_be.migration;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One transactional change: preflight all rows before touching authors or deleting drafts. */
public class UnifyDocumentDrafter implements CustomTaskChange {
    private static final String UNKNOWN_AUTHOR = """
            NOT EXISTS (SELECT 1 FROM user_logins u WHERE u.id = d.drafter_id)
            AND NOT EXISTS (SELECT 1 FROM user_logins u WHERE u.id = d.creator_user_id)
            """;
    private static final String SAFE_DRAFT = """
            d.status = 'DRAFT' AND d.submitted_at IS NULL
            AND NULLIF(TRIM(d.primary_file_path), '') IS NULL
            AND NULLIF(TRIM(d.official_file_path), '') IS NULL
            AND NULLIF(TRIM(d.official_number), '') IS NULL
            AND NULLIF(TRIM(d.official_hash), '') IS NULL
            AND NOT EXISTS (SELECT 1 FROM document_approval_requests r WHERE r.document_id = d.id)
            AND NOT EXISTS (SELECT 1 FROM document_workflow_logs w WHERE w.document_id = d.id)
            AND NOT EXISTS (SELECT 1 FROM document_distributions x WHERE x.document_id = d.id)
            AND NOT EXISTS (SELECT 1 FROM documents child WHERE child.parent_document_id = d.id)
            AND NOT EXISTS (SELECT 1 FROM document_process_logs p WHERE p.document_id = d.id
                AND (p.action IS NULL OR p.action NOT IN ('CREATE_DRAFT', 'UPDATE_DRAFT',
                    'UPLOAD_ATTACHMENT', 'DELETE_ATTACHMENT', 'RESTORE_VERSION', 'DELETE_DRAFT')))
            AND NOT EXISTS (SELECT 1 FROM document_attachments a
                JOIN document_versions v ON v.id = a.document_version_id
                WHERE v.document_id = d.id AND a.document_id <> d.id)
            """;
    private String confirmation;

    @Override
    public void execute(Database database) throws CustomChangeException {
        try {
            Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
            List<Long> unknown = ids(connection, "SELECT d.id FROM documents d WHERE " + UNKNOWN_AUTHOR);
            List<Long> trash = ids(connection, "SELECT d.id FROM documents d WHERE " + UNKNOWN_AUTHOR
                    + " AND " + SAFE_DRAFT);
            List<Long> blocked = new ArrayList<>(unknown);
            blocked.removeAll(trash);
            if (!blocked.isEmpty()) {
                throw new CustomChangeException("Cannot determine document drafter; verify document IDs: " + blocked);
            }

            // Metadata preflight also happens before mutation. Only the retired FK is removed.
            String table = documentTable(connection);
            Set<String> retiredForeignKeys = new LinkedHashSet<>();
            boolean drafterForeignKey = false;
            try (ResultSet keys = connection.getMetaData().getImportedKeys(
                    connection.getCatalog(), connection.getSchema(), table)) {
                while (keys.next()) {
                    String column = keys.getString("FKCOLUMN_NAME");
                    if ("creator_user_id".equalsIgnoreCase(column)) {
                        retiredForeignKeys.add(keys.getString("FK_NAME"));
                    }
                    if ("drafter_id".equalsIgnoreCase(column)) {
                        if (!"user_logins".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                                || !"id".equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) {
                            throw new CustomChangeException("Unexpected foreign key for documents.drafter_id");
                        }
                        drafterForeignKey = true;
                    }
                }
            }
            try (Statement sql = connection.createStatement()) {
                int copied = sql.executeUpdate("""
                        UPDATE documents SET drafter_id = creator_user_id
                        WHERE NOT EXISTS (SELECT 1 FROM user_logins u WHERE u.id = documents.drafter_id)
                        AND EXISTS (SELECT 1 FROM user_logins u WHERE u.id = documents.creator_user_id)
                        """);
                // Individual IDs come from the preflight query, never from user input.
                for (Long id : trash) {
                    sql.executeUpdate("DELETE FROM document_attachments WHERE document_id = " + id);
                    sql.executeUpdate("DELETE FROM document_process_logs WHERE document_id = " + id);
                    sql.executeUpdate("DELETE FROM document_versions WHERE document_id = " + id);
                    sql.executeUpdate("DELETE FROM documents WHERE id = " + id);
                }
                if (!drafterForeignKey) {
                    sql.execute("ALTER TABLE documents ADD CONSTRAINT fk_documents_drafter "
                            + "FOREIGN KEY (drafter_id) REFERENCES user_logins(id)");
                }
                sql.execute("ALTER TABLE documents ALTER COLUMN drafter_id SET NOT NULL");
                for (String name : retiredForeignKeys) {
                    sql.execute("ALTER TABLE documents DROP CONSTRAINT " + quote(name));
                }
                sql.execute("ALTER TABLE documents DROP COLUMN creator_user_id");
                confirmation = "Document drafter unified; copied=" + copied + ", deleted draft IDs=" + trash;
            }
        } catch (SQLException e) {
            throw new CustomChangeException("Unable to unify document drafter", e);
        }
    }

    private static List<Long> ids(Connection connection, String query) throws SQLException {
        List<Long> result = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery(query + " ORDER BY d.id")) {
            while (rows.next()) result.add(rows.getLong(1));
        }
        return result;
    }

    private static String documentTable(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if ("documents".equalsIgnoreCase(tables.getString("TABLE_NAME"))) return tables.getString("TABLE_NAME");
            }
        }
        throw new SQLException("documents table not found");
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override public String getConfirmationMessage() { return confirmation; }
    @Override public void setUp() { }
    @Override public void setFileOpener(ResourceAccessor accessor) { }
    @Override public ValidationErrors validate(Database database) { return new ValidationErrors(); }
}

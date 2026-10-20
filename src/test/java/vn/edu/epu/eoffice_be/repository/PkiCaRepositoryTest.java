package vn.edu.epu.eoffice_be.repository;

import jakarta.persistence.EntityManager;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Isolated H2 database: no Spring startup, local env, real keys or application database. */
class PkiCaRepositoryTest {
    private static SessionFactory factory;
    private EntityManager em;
    private PkiCaRepository repository;
    @TempDir java.nio.file.Path directory;

    @Test
    void caLifecycleEncryptsPersistsAndReloadsThroughRealJpaRepository() {
        var properties = new vn.edu.epu.eoffice_be.config.PkiProperties();
        properties.setCaKeystorePath(directory.resolve("ca.p12").toString());
        properties.setCaCertPath(directory.resolve("ca.crt").toString());
        properties.setRevokedPath(directory.resolve("revoked.json").toString());
        properties.setUserKeystoresDir(directory.resolve("users").toString());
        properties.setDefaultKeystorePassword("synthetic-integration-password");
        var encryptionProperties = new vn.edu.epu.eoffice_be.config.PkiEncryptionProperties();
        encryptionProperties.setKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        encryptionProperties.setKeyId("synthetic-integration-key");
        var encryption = new vn.edu.epu.eoffice_be.service.PkiEncryptionService(encryptionProperties);
        var history = new vn.edu.epu.eoffice_be.service.PkiSigningHistory(null) {
            @Override public boolean hasAnySigningHistory() { return false; }
        };
        var service = new vn.edu.epu.eoffice_be.service.PkiCaService(properties, history, repository, encryption);
        var created = service.initialize();
        em.flush();
        em.clear();
        var freshService = new vn.edu.epu.eoffice_be.service.PkiCaService(properties, history, repository,
                new vn.edu.epu.eoffice_be.service.PkiEncryptionService(encryptionProperties));
        assertEquals(created.fingerprintSha256(), freshService.status().fingerprintSha256());
        assertNotNull(freshService.requireValidCa().privateKey());
        assertEquals(1, repository.count());
        assertFalse(java.nio.file.Files.exists(properties.caKeystore()));
        assertFalse(java.nio.file.Files.exists(properties.caCertificate()));
    }

    @BeforeAll
    static void schema() throws Exception {
        String url = "jdbc:h2:mem:pki-repository-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var liquibase = new Liquibase("db/changelog/master-test.yml",
                     new ClassLoaderResourceAccessor(), new JdbcConnection(connection))) {
            liquibase.update(new Contexts());
        }
        factory = new Configuration().addAnnotatedClass(PkiCaEntity.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                // Liquibase maps TEXT to H2 CLOB; PostgreSQL uses TEXT. Exercise actual CRUD
                // rather than Hibernate's cross-database JDBC type comparison.
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterAll
    static void shutdown() {
        if (factory != null) factory.close();
    }

    @BeforeEach
    void transaction() {
        em = factory.createEntityManager();
        em.getTransaction().begin();
        repository = new JpaRepositoryFactory(em).getRepository(PkiCaRepository.class);
    }

    @AfterEach
    void rollback() {
        if (em.getTransaction().isActive()) em.getTransaction().rollback();
        em.close();
    }

    @Test
    void savesAndReadsAllEightFieldsAfterClearingPersistenceContext() {
        var ca = ca(PkiCaEntity.Status.ACTIVE, "a");
        Instant before = Instant.now();
        repository.saveAndFlush(ca);
        Long id = ca.getId();
        assertNotNull(id);
        assertNotNull(ca.getCreatedAt());
        assertFalse(ca.getCreatedAt().isBefore(before));
        em.clear();
        var loaded = repository.findActive().orElseThrow();
        assertEquals(id, loaded.getId());
        assertEquals(ca.getStatus(), loaded.getStatus());
        assertEquals(ca.getCertificatePem(), loaded.getCertificatePem());
        assertArrayEquals(ca.getKeystoreEncrypted(), loaded.getKeystoreEncrypted());
        assertArrayEquals(ca.getEncryptionNonce(), loaded.getEncryptionNonce());
        assertEquals(ca.getEncryptionKeyId(), loaded.getEncryptionKeyId());
        assertEquals(ca.getFingerprintSha256(), loaded.getFingerprintSha256());
        assertEquals(ca.getCreatedAt().getEpochSecond(), loaded.getCreatedAt().getEpochSecond());
    }

    @Test
    void retiredCaIsRetainedButNotSelectedAsActive() {
        repository.saveAndFlush(ca(PkiCaEntity.Status.RETIRED, "b"));
        em.clear();
        assertTrue(repository.findActive().isEmpty());
        assertEquals(1, repository.count());
        repository.saveAndFlush(ca(PkiCaEntity.Status.ACTIVE, "c"));
        em.clear();
        assertEquals("c".repeat(64), repository.findActive().orElseThrow().getFingerprintSha256());
    }

    @Test
    void databaseRejectsDuplicateCertificateFingerprint() {
        repository.saveAndFlush(ca(PkiCaEntity.Status.RETIRED, "d"));
        assertThrows(RuntimeException.class,
                () -> repository.saveAndFlush(ca(PkiCaEntity.Status.RETIRED, "d")));
    }

    private PkiCaEntity ca(PkiCaEntity.Status status, String fingerprintCharacter) {
        var ca = new PkiCaEntity();
        ca.setStatus(status);
        ca.setCertificatePem("synthetic-public-certificate");
        ca.setKeystoreEncrypted(new byte[32]);
        ca.setEncryptionNonce(new byte[12]);
        ca.setEncryptionKeyId("synthetic-test-key");
        ca.setFingerprintSha256(fingerprintCharacter.repeat(64));
        return ca;
    }
}

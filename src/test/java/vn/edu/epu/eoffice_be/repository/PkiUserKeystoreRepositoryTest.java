package vn.edu.epu.eoffice_be.repository;

import vn.edu.epu.eoffice_be.security.pki.PkiTestSamples;
import vn.edu.epu.eoffice_be.security.pki.FastPkiTest;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.epu.eoffice_be.config.PkiEncryptionProperties;
import vn.edu.epu.eoffice_be.config.PkiProperties;
import vn.edu.epu.eoffice_be.entity.PkiCaEntity;
import vn.edu.epu.eoffice_be.entity.PkiUserKeystoreEntity;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.security.pki.Crypto;
import vn.edu.epu.eoffice_be.security.pki.PdfSignatureVerifier;
import vn.edu.epu.eoffice_be.security.pki.PdfSigner;
import vn.edu.epu.eoffice_be.service.*;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real repositories and transactions on isolated H2; never reads application env or real keys. */
@FastPkiTest
class PkiUserKeystoreRepositoryTest {
    private static SessionFactory factory;
    private static String url;
    @TempDir Path directory;
    private TransactionTemplate tx;
    private PkiCaRepository caRepository;
    private PkiUserKeystoreRepository repository;
    private UserLoginRepository users;
    private PkiProperties properties;
    private PkiCaService caService;
    private PkiEncryptionService encryption;
    private PkiSigningHistory history;
    private Long userId;
    private Long caId;

    @BeforeAll
    static void schema() throws Exception {
        url = "jdbc:h2:mem:pki-user-repository-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000;DEFAULT_NULL_ORDERING=HIGH";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var liquibase = new Liquibase("db/changelog/master-test.yml",
                     new ClassLoaderResourceAccessor(), new JdbcConnection(connection))) {
            liquibase.update(new Contexts());
        }
        factory = new Configuration().addAnnotatedClass(PkiCaEntity.class)
                .addAnnotatedClass(PkiUserKeystoreEntity.class).addAnnotatedClass(UserLogin.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterAll static void closeFactory() { if (factory != null) factory.close(); }

    @BeforeEach
    void setup() {
        var manager = new JpaTransactionManager(factory);
        manager.setJpaDialect(new HibernateJpaDialect());
        tx = new TransactionTemplate(manager);
        var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        var repositories = new JpaRepositoryFactory(em);
        caRepository = repositories.getRepository(PkiCaRepository.class);
        repository = repositories.getRepository(PkiUserKeystoreRepository.class);
        users = repositories.getRepository(UserLoginRepository.class);
        userId = tx.execute(s -> ((Number) em.createNativeQuery("SELECT MIN(id) FROM user_logins WHERE delete_flg = false")
                .getSingleResult()).longValue());
        properties = new PkiProperties();
        properties.setCaKeystorePath(directory.resolve("ca.p12").toString());
        properties.setCaCertPath(directory.resolve("ca.crt").toString());
        properties.setUserKeystoresDir(directory.resolve("users").toString());
        properties.setRevokedPath(directory.resolve("revoked.json").toString());
        properties.setDefaultKeystorePassword("synthetic-integration-password");
        var encryptionProperties = new PkiEncryptionProperties();
        encryptionProperties.setKey(Base64.getEncoder().encodeToString(new byte[32]));
        encryptionProperties.setKeyId("synthetic-integration-key");
        encryption = new PkiEncryptionService(encryptionProperties);
        history = new PkiSigningHistory(new JdbcTemplate(new DriverManagerDataSource(url, "sa", "")));
        caService = new PkiCaService(properties, history, caRepository, encryption);
        caId = tx.execute(s -> caRepository.saveAndFlush(
                PkiTestSamples.caRecord(
                        PkiTestSamples.root(), properties, encryption)).getId());
        assertNotNull(tx.execute(s -> caService.requireValidCa()).privateKey());
    }

    @AfterEach
    void cleanup() {
        if (tx != null) tx.executeWithoutResult(s -> {
            repository.deleteAllInBatch();
            caRepository.deleteAllInBatch();
        });
    }

    private PkiUserKeystoreService service() {
        return new PkiUserKeystoreService(properties, caService, caRepository, repository, users, history, encryption);
    }

    private PdfSigner signer() {
        var signer = new PdfSigner(service());
        ReflectionTestUtils.setField(signer, "signaturesDir", directory.resolve("images").toString());
        ReflectionTestUtils.setField(signer, "stampsDir", directory.resolve("stamps").toString());
        return signer;
    }

    @Test
    void committedKeystoreIsReusedAcrossNewTransactionsAndSignerInstances() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf = output.toByteArray();
        }
        var first = tx.execute(s -> signer().signBytes(pdf, userId.toString(), "Test signer", "First"));
        var record = tx.execute(s -> repository.findByUserIdAndCaId(userId, caId).orElseThrow());
        assertNotNull(record.getId());
        assertEquals(userId, record.getUserId());
        assertEquals(caId, record.getCaId());
        assertEquals(first.serialHex(), record.getCertificateSerial());
        assertEquals("synthetic-integration-key", record.getEncryptionKeyId());
        assertEquals(12, record.getEncryptionNonce().length);
        assertNotNull(record.getCreatedAt());
        assertEquals(first.serialHex(), Crypto.decodeCertPem(record.getCertificatePem()).getSerialNumber().toString(16));
        assertThrows(IllegalArgumentException.class,
                () -> Crypto.decodeP12(record.getKeystoreEncrypted(), properties.password()));

        var second = tx.execute(s -> signer().signBytes(pdf, userId.toString(), "Changed name", "Second"));
        assertEquals(first.serialHex(), second.serialHex());
        assertEquals(first.signerCN(), second.signerCN());
        var reloaded = tx.execute(s -> repository.findByUserIdAndCaId(userId, caId).orElseThrow());
        assertArrayEquals(record.getKeystoreEncrypted(), reloaded.getKeystoreEncrypted());
        assertEquals(1L, tx.<Long>execute(s -> repository.count()).longValue());
        assertFalse(Files.exists(properties.userKeystores()));
        assertFalse(Files.exists(properties.caKeystore()));
        assertEquals("VALID", tx.execute(s -> new PdfSignatureVerifier(caService, properties)
                .verify(second.signedPdfBytes()).getOverallStatus()));
    }

    @Test
    void concurrentTransactionsIssueExactlyOneKeystoreAndBothReuseIt() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> attempt = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return tx.execute(s -> {
                    service().ensure(userId.toString(), "Concurrent signer", null, null);
                    return repository.findByUserIdAndCaId(userId, caId).orElseThrow().getCertificateSerial();
                });
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertEquals(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
        }
        assertEquals(1L, tx.<Long>execute(s -> repository.count()).longValue());
    }

    @Test
    void uniqueOwnerCaAndForeignKeysRemainEnforcedByDatabase() {
        var sample = PkiTestSamples.user(
                PkiTestSamples.root(), userId.toString(), "Test signer", null, null);
        tx.executeWithoutResult(s -> repository.saveAndFlush(
                PkiTestSamples.userRecord(sample, userId, caId, properties, encryption)));
        var original = tx.execute(s -> repository.findByUserIdAndCaId(userId, caId).orElseThrow());
        var duplicate = copy(original);
        duplicate.setCertificateSerial("different-serial");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> repository.saveAndFlush(duplicate)));
        var unknownOwner = copy(original);
        unknownOwner.setUserId(Long.MAX_VALUE);
        unknownOwner.setCertificateSerial("unknown-owner-serial");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> repository.saveAndFlush(unknownOwner)));
        var unknownCa = copy(original);
        unknownCa.setCaId(Long.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> repository.saveAndFlush(unknownCa)));
        assertEquals(1L, tx.<Long>execute(s -> repository.count()).longValue());
    }

    private PkiUserKeystoreEntity copy(PkiUserKeystoreEntity source) {
        var target = new PkiUserKeystoreEntity();
        target.setUserId(source.getUserId()); target.setCaId(source.getCaId());
        target.setCertificatePem(source.getCertificatePem()); target.setCertificateSerial(source.getCertificateSerial());
        target.setKeystoreEncrypted(source.getKeystoreEncrypted().clone());
        target.setEncryptionNonce(source.getEncryptionNonce().clone()); target.setEncryptionKeyId(source.getEncryptionKeyId());
        return target;
    }
}

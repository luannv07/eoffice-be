package vn.edu.epu.eoffice_be.config;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class LiquibaseConfigTest {

    @Test
    void liquibaseBeanShouldUseProvidedDatasourceAndChangelog() {
        LiquibaseConfig config = new LiquibaseConfig();
        DataSource dataSource = mock(DataSource.class);

        SpringLiquibase liquibase = config.liquibase(dataSource);

        assertNotNull(liquibase);
        assertEquals(dataSource, liquibase.getDataSource());
        assertEquals("classpath:db/changelog/master.yml", liquibase.getChangeLog());
    }

    @Test
    void liquibaseBeanShouldBeCreatedConsistentlyAcrossInvocations() {
        LiquibaseConfig config = new LiquibaseConfig();
        DataSource dataSource = mock(DataSource.class);
        SpringLiquibase first = config.liquibase(dataSource);
        SpringLiquibase second = config.liquibase(dataSource);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("classpath:db/changelog/master.yml", second.getChangeLog());
    }
}

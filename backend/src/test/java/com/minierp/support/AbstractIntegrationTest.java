package com.minierp.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base class for integration tests: real PostgreSQL (test profile), Flyway-migrated
 * schema, and a deterministic data reset before every test method.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/db/test-reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public static final String ADMIN = "admin";
    public static final String OPS = "ops";
    public static final String SALES = "sales";

    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String OPS_PASSWORD = "Ops@123";
    public static final String SALES_PASSWORD = "Sales@123";

    /**
     * Runs work inside a real transaction, mirroring how production callers own the
     * boundary when they invoke MANDATORY-propagation ledger methods.
     */
    protected void inTx(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
            return new TransactionTemplate(tm);
        }
    }
}

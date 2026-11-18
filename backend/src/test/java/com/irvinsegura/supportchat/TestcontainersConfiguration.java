package com.irvinsegura.supportchat;

import com.irvinsegura.supportchat.support.NullFieldGuard;
import com.irvinsegura.supportchat.support.TestClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL with pgvector, so vector search, constraints and row-level security behave as in production.
 * Flyway migrates as the container's superuser while the application connects as the restricted role from
 * {@code db/create-app-role.sql}; connecting as the superuser would bypass row-level security and hide leaks.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(NullFieldGuard.class)
public class TestcontainersConfiguration {

    public static final String APP_USER = "supportchat_app";
    public static final String APP_PASSWORD = "app-test-password";

    @Bean
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"))
                .withInitScript("db/create-app-role.sql");
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> APP_USER);
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
            registry.add("spring.flyway.placeholders.appRole", () -> APP_USER);
        };
    }

    /**
     * The application's clock, which a test may move ahead; {@code ResetTestClock} puts it back after every test.
     */
    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock();
    }
}

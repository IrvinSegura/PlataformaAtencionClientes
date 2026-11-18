package com.irvinsegura.supportchat.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * As in production, actuator runs on its own port: the metrics collector reads Prometheus metrics there, while the
 * public port only answers health probes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("test")
class ActuatorAccessIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    // A real port starts Tomcat, and with it the database, before TestcontainersConfiguration's registrar runs, so
    // this test registers its database the earlier way
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withInitScript("db/create-app-role.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> TestcontainersConfiguration.APP_USER);
        registry.add("spring.datasource.password", () -> TestcontainersConfiguration.APP_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.appRole", () -> TestcontainersConfiguration.APP_USER);
    }

    @Value("${local.server.port}")
    private int serverPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Test
    void metricsAreOnlyServedOnTheManagementPort() throws Exception {
        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("jvm_memory_used_bytes");

        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isIn(401, 403, 404);
        // Nothing else of actuator opens up on the private port
        assertThat(get(managementPort, "/actuator/modulith").statusCode()).isIn(401, 403);
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}

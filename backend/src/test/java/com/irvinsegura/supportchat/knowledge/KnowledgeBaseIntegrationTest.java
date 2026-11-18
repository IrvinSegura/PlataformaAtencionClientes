package com.irvinsegura.supportchat.knowledge;

import static com.irvinsegura.supportchat.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Upload, background ingestion, hybrid search, and isolation between workspaces enforced by the database itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KnowledgeBaseIntegrationTest {

    private static final String SHIPPING = """
            # Envíos

            Los pedidos se envían en 24 horas hábiles. El envío nacional tarda de 3 a 5 días hábiles y es gratis \
            en compras mayores a 999 pesos.

            # Devoluciones

            Aceptamos devoluciones dentro de los 30 días posteriores a la entrega, con el producto sin usar.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc);
    }

    @Test
    void anUploadedDocumentBecomesSearchable() throws Exception {
        Operator operator = api.newOperator("Acme");
        UUID documentId = api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);

        api.getAs(operator, "/api/v1/knowledge/documents/" + documentId)
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.mediaType").value("text/markdown"));
        api.postJsonAs(operator, "/api/v1/knowledge/search", json("{'question':'¿Cuánto tarda el envío?'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(documentId.toString()))
                .andExpect(jsonPath("$[0].content").value(org.hamcrest.Matchers.containsString("3 a 5 días")));
    }

    @Test
    void theSameFileCannotBeUploadedTwice() throws Exception {
        Operator operator = api.newOperator("Acme");
        api.upload(operator, "faq.txt", "Horario de 9 a 18 horas.").andExpect(status().isAccepted());

        api.upload(operator, "copy.txt", "Horario de 9 a 18 horas.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    void unsupportedFilesAreRejected() throws Exception {
        Operator operator = api.newOperator("Acme");

        api.upload(operator, "script.exe", "MZ binary")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DOCUMENT"));
    }

    @Test
    void aDeletedDocumentStopsBeingUsedAtOnce() throws Exception {
        Operator owner = api.newOperator("Acme");
        UUID documentId = api.uploadAndWaitUntilReady(owner, "envios.md", SHIPPING);

        mockMvc.perform(delete("/api/v1/knowledge/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());

        api.getAs(owner, "/api/v1/knowledge/documents/" + documentId).andExpect(status().isNotFound());
        api.postJsonAs(owner, "/api/v1/knowledge/search", json("{'question':'¿Cuánto tarda el envío?'}"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void oneWorkspaceCanNeverSeeAnotherWorkspacesDocuments() throws Exception {
        Operator acme = api.newOperator("Acme");
        Operator rival = api.newOperator("Rival");
        UUID documentId = api.uploadAndWaitUntilReady(acme, "envios.md", SHIPPING);

        api.getAs(rival, "/api/v1/knowledge/documents/" + documentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        api.getAs(rival, "/api/v1/knowledge/documents").andExpect(jsonPath("$.total").value(0));
        api.postJsonAs(rival, "/api/v1/knowledge/search", json("{'question':'¿Cuánto tarda el envío?'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/v1/knowledge/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, rival.bearer()))
                .andExpect(status().isNotFound());
        api.getAs(acme, "/api/v1/knowledge/documents/" + documentId).andExpect(status().isOk());
    }

    @Test
    void withoutABoundWorkspaceTheDatabaseReturnsNothing() throws Exception {
        Operator operator = api.newOperator("Acme");
        api.uploadAndWaitUntilReady(operator, "envios.md", SHIPPING);

        // A query that forgot the tenant (no transaction binding it) fails closed instead of leaking every row
        assertThat(jdbc.sql("SELECT count(*) FROM documents").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM document_chunks").query(Long.class).single()).isZero();
    }
}

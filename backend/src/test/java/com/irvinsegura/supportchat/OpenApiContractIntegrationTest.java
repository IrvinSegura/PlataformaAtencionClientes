package com.irvinsegura.supportchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The operator API as OpenAPI, kept in {@code docs/openapi.json}: the panel generates its types from that file, so a
 * change to a request or a response that the file does not have yet fails here, before the panel finds out in
 * production. After a deliberate change, write the file again with
 * {@code ./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true} and run {@code npm run api:types} in
 * panel/.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.writer-with-order-by-keys=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiContractIntegrationTest {

    private static final Path CONTRACT = Path.of("..", "docs", "openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theApiMatchesTheContractThePanelIsBuiltOn() throws Exception {
        String served = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode api = JSON.readTree(served);
        String current = JSON.writeValueAsString(api).replace("\r\n", "\n") + "\n";

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(CONTRACT, current, StandardCharsets.UTF_8);
        }
        // Writing a missing file would let a deleted contract pass unnoticed
        assertThat(CONTRACT)
                .as("docs/openapi.json is missing: write it with -Dopenapi.update=true")
                .exists();

        String kept = Files.readString(CONTRACT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(JSON.readTree(kept))
                .as("The API changed: write docs/openapi.json again with -Dopenapi.update=true and run "
                        + "npm run api:types in panel/")
                .isEqualTo(api);
    }
}

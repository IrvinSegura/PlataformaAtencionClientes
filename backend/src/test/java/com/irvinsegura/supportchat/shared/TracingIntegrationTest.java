package com.irvinsegura.supportchat.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.Eventually;
import com.irvinsegura.supportchat.support.ExternalServices;
import com.irvinsegura.supportchat.support.ExternalServices.Request;
import com.irvinsegura.supportchat.support.WithExternalServices;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Requests leave traces over OTLP to whatever collector the environment names, tagged with the service, so Grafana
 * (or any OpenTelemetry backend) shows where a slow answer spent its time.
 */
class TracingIntegrationTest extends WithExternalServices {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestsAreExportedAsSpansOverOtlp() throws Exception {
        ApiClient api = new ApiClient(mockMvc);
        Operator owner = api.newOperator("Acme");

        api.getAs(owner, "/api/v1/workspace").andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(15), () -> {
            List<Request> exports = ExternalServices.requests(request -> request.method().equals("POST")
                    && request.path().equals("/otlp/v1/traces"));
            // The body is protobuf: its strings travel as plain UTF-8
            assertThat(exports).anySatisfy(export -> assertThat(export.body())
                    .contains("support-chat-platform")
                    .contains("/api/v1/workspace"));
        });
    }
}

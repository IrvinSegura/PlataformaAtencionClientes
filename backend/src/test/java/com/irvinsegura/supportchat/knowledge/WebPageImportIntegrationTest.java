package com.irvinsegura.supportchat.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.Eventually;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A business imports pages of its website as knowledge. The test profile lets the fetcher reach this machine, so a
 * small local site stands in for the business's one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebPageImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator operator;
    private HttpServer site;
    private String base;
    private final AtomicReference<String> shippingPage = new AtomicReference<>(
            "<p>El envío nacional tarda de 3 a 5 días hábiles.</p>");

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        operator = api.newOperator("Acme");
        site = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        site.createContext("/ayuda", exchange -> serve(exchange.getResponseBody(), exchange, """
                <html><head><title>Centro de ayuda</title></head><body>
                <h1>Centro de ayuda</h1><p>Atendemos de lunes a viernes de 9 a 18 h.</p>
                <a href="/envios">Envíos</a> <a href="/devoluciones">Devoluciones</a>
                <a href="https://otro-sitio.example/">Otro sitio</a>
                </body></html>"""));
        site.createContext("/envios", exchange -> serve(exchange.getResponseBody(), exchange,
                "<html><head><title>Envíos</title></head><body>" + shippingPage.get() + "</body></html>"));
        site.createContext("/devoluciones", exchange -> serve(exchange.getResponseBody(), exchange,
                "<html><head><title>Devoluciones</title></head><body><p>Devoluciones en 30 días.</p></body></html>"));
        site.start();
        base = "http://127.0.0.1:" + site.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        site.stop(0);
    }

    @Test
    void aPageAndTheSitePagesItLinksToBecomeSearchable() throws Exception {
        String body = api.postJsonAs(operator, "/api/v1/knowledge/documents/web",
                        ApiClient.json("{'url':'%s/ayuda','linkedPages':5}", base))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceUrl").value(base + "/ayuda"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        awaitReady(id);
        Eventually.assertEventually(Duration.ofSeconds(30), () -> {
            String page = api.getAs(operator, "/api/v1/knowledge/documents?size=100")
                    .andReturn().getResponse().getContentAsString();
            List<String> urls = JsonPath.read(page, "$.items[?(@.status == 'READY')].sourceUrl");
            // Only pages of the same site are followed
            assertThat(urls).containsExactlyInAnyOrder(base + "/ayuda", base + "/envios", base + "/devoluciones");
        });
        String document = api.getAs(operator, "/api/v1/knowledge/documents/" + id)
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(document, "$.title")).isEqualTo("Centro de ayuda");
        assertThat((Integer) JsonPath.read(document, "$.embeddedChunks")).isPositive();

        api.postJsonAs(operator, "/api/v1/knowledge/search", "{\"question\":\"¿Cuánto tarda el envío?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value(org.hamcrest.Matchers.containsString("3 a 5 días")));
    }

    @Test
    void reprocessingAPageDownloadsItAgain() throws Exception {
        String body = api.postJsonAs(operator, "/api/v1/knowledge/documents/web",
                        // Leaving linkedPages out imports this page only
                        ApiClient.json("{'url':'%s/envios'}", base))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        awaitReady(id);

        shippingPage.set("<p>Ahora el envío nacional llega en 24 horas.</p>");
        api.postAs(operator, "/api/v1/knowledge/documents/" + id + "/reprocess").andExpect(status().isAccepted());

        Eventually.assertEventually(Duration.ofSeconds(30), () ->
                api.postJsonAs(operator, "/api/v1/knowledge/search", "{\"question\":\"envío nacional\"}")
                        .andExpect(jsonPath("$[0].content")
                                .value(org.hamcrest.Matchers.containsString("24 horas"))));
    }

    @Test
    void anAddressThatIsNotAWebPageIsRefused() throws Exception {
        api.postJsonAs(operator, "/api/v1/knowledge/documents/web", "{\"url\":\"ftp://example.com/x\"}")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void agentsCannotImportPages() throws Exception {
        Operator agent = api.newMember(operator, "AGENT");

        api.postJsonAs(agent, "/api/v1/knowledge/documents/web", ApiClient.json("{'url':'%s/ayuda'}", base))
                .andExpect(status().isForbidden());
    }

    private void awaitReady(String id) throws Exception {
        Eventually.assertEventually(Duration.ofSeconds(30), () -> {
            String document = api.getAs(operator, "/api/v1/knowledge/documents/" + id)
                    .andReturn().getResponse().getContentAsString();
            assertThat((String) JsonPath.read(document, "$.status")).isEqualTo("READY");
        });
    }

    private static void serve(OutputStream out, com.sun.net.httpserver.HttpExchange exchange, String html)
            throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (out) {
            out.write(body);
        }
    }
}

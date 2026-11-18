package com.irvinsegura.supportchat.knowledge.infrastructure.fetching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.WebAddress;
import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher;
import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher.FetchedPage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpWebPageFetcherTest {

    private static final String PAGE = """
            <html><head><title>Preguntas frecuentes</title></head>
            <body><p>Enviamos a todo México.</p>
            <a href="/envios">Envíos</a> <a href="https://otro-sitio.example/x">Fuera</a></body></html>
            """;

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/faq", exchange -> respond(exchange, 200, "text/html; charset=utf-8",
                PAGE.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/moved", exchange -> redirect(exchange, "/faq"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/file.pdf", exchange -> respond(exchange, 200, "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        server.createContext("/huge", exchange -> {
            byte[] chunk = new byte[64 * 1024];
            Arrays.fill(chunk, (byte) 'a');
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (long sent = 0; sent <= KnowledgeLimits.MAX_WEB_PAGE_BYTES; sent += chunk.length) {
                    out.write(chunk);
                }
            } catch (IOException e) {
                // The fetcher hangs up once the limit is passed
            }
        });
        server.createContext("/", exchange -> respond(exchange, 404, "text/html", new byte[0]));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void downloadsAPageWithItsTitleAndLinks() {
        FetchedPage page = fetcher(true).fetch(WebAddress.parse(base + "/faq"));

        assertThat(page.title()).isEqualTo("Preguntas frecuentes");
        assertThat(new String(page.content(), StandardCharsets.UTF_8)).contains("Enviamos a todo México.");
        assertThat(page.links()).containsExactly(base + "/envios", "https://otro-sitio.example/x");
    }

    @Test
    void followsRedirectsAndReportsWhereThePageWasFound() {
        FetchedPage page = fetcher(true).fetch(WebAddress.parse(base + "/moved"));

        assertThat(page.address().value()).isEqualTo(base + "/faq");
        assertThat(page.title()).isEqualTo("Preguntas frecuentes");
    }

    @Test
    void stopsAfterAFewRedirects() {
        assertThatThrownBy(() -> fetcher(true).fetch(WebAddress.parse(base + "/loop")))
                .isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("redirects");
    }

    @Test
    void refusesWhatIsNotAWebPage() {
        assertThatThrownBy(() -> fetcher(true).fetch(WebAddress.parse(base + "/file.pdf")))
                .isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("not a web page");
    }

    @Test
    void reportsAMissingPage() {
        assertThatThrownBy(() -> fetcher(true).fetch(WebAddress.parse(base + "/nope")))
                .isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("404");
    }

    @Test
    void stopsReadingAPageThatIsTooLarge() {
        assertThatThrownBy(() -> fetcher(true).fetch(WebAddress.parse(base + "/huge")))
                .isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void neverReachesPrivateOrLocalNetworksInProduction() {
        WebPageFetcher fetcher = fetcher(false);

        for (String address : new String[] {"http://127.0.0.1/faq", "http://10.0.0.5/", "http://192.168.1.1/",
                "http://169.254.169.254/latest/meta-data/", "http://[::1]/"}) {
            assertThatThrownBy(() -> fetcher.fetch(WebAddress.parse(address)))
                    .as(address)
                    .isInstanceOf(UnreadableDocumentException.class)
                    .hasMessageContaining("private");
        }
    }

    @Test
    void onlyStandardWebPortsInProduction() {
        assertThatThrownBy(() -> fetcher(false).fetch(WebAddress.parse(base + "/faq")))
                .isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("standard web ports");
    }

    private static WebPageFetcher fetcher(boolean allowPrivateNetwork) {
        return WebFetchers.http(Duration.ofSeconds(5), allowPrivateNetwork);
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}

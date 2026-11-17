package com.irvinsegura.supportchat.knowledge.infrastructure.fetching;

import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.WebAddress;
import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher;
import com.irvinsegura.supportchat.shared.infrastructure.NetworkAddresses;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Downloads the pages a workspace imports, which makes the platform fetch addresses its customers chose; the rules
 * below keep that from reaching anything but public websites (server-side request forgery):
 * <ul>
 *   <li>only http and https on their default ports, and every address the host resolves to must be public;</li>
 *   <li>redirects are followed by hand, a few at most, and each target is checked like the first address;</li>
 *   <li>only HTML is accepted, and the body is read up to {@link KnowledgeLimits#MAX_WEB_PAGE_BYTES}.</li>
 * </ul>
 * A name that resolves differently between the check and the connection (DNS rebinding) remains possible; as for
 * action webhooks, a deployment that must close it routes this traffic through an egress proxy.
 * <p>
 * Development and tests may allow private addresses and other ports, to import pages served on the same machine.
 */
final class HttpWebPageFetcher implements WebPageFetcher {

    static final String USER_AGENT = "SupportChatBot/1.0 (+knowledge import requested by the site's owner)";
    private static final int MAX_REDIRECTS = 5;

    private final HttpClient http;
    private final Duration timeout;
    private final boolean allowPrivateNetwork;

    HttpWebPageFetcher(HttpClient http, Duration timeout, boolean allowPrivateNetwork) {
        this.http = http;
        this.timeout = timeout;
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    @Override
    public FetchedPage fetch(WebAddress address) {
        URI current = address.uri();
        for (int redirects = 0; ; redirects++) {
            check(current);
            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                close(response);
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty() || redirects >= MAX_REDIRECTS) {
                    throw new UnreadableDocumentException("The page redirects too many times");
                }
                try {
                    current = current.resolve(location.get().strip());
                } catch (IllegalArgumentException e) {
                    throw new UnreadableDocumentException("The page redirects to an invalid address");
                }
                continue;
            }
            if (status != 200) {
                close(response);
                throw new UnreadableDocumentException(status == 404 || status == 410
                        ? "The page does not exist (HTTP %d)".formatted(status)
                        : "The site answered HTTP %d; check that the page is public".formatted(status));
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.isEmpty() && !contentType.startsWith("text/html")
                    && !contentType.startsWith("application/xhtml+xml")) {
                close(response);
                throw new UnreadableDocumentException("The address is not a web page (" + contentType
                        + "); upload files such as PDFs directly");
            }
            byte[] body = read(response);
            WebAddress landed = WebAddress.parse(current.toString());
            return parse(landed, body, contentType);
        }
    }

    private void check(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UnreadableDocumentException("Only http and https pages can be imported");
        }
        if (uri.getHost() == null) {
            throw new UnreadableDocumentException("The address has no host");
        }
        if (allowPrivateNetwork) {
            return;
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw new UnreadableDocumentException("Only pages on the standard web ports can be imported");
        }
        try {
            if (!NetworkAddresses.allPublic(uri.getHost())) {
                throw new UnreadableDocumentException("Pages on private, local or reserved networks cannot be "
                        + "imported");
            }
        } catch (UnknownHostException e) {
            throw new UnreadableDocumentException("The site's name does not exist: " + uri.getHost());
        }
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                .header("Accept-Language", "es,en;q=0.8,*;q=0.5")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new UnreadableDocumentException("The site took too long to answer");
        } catch (IOException e) {
            throw new UnreadableDocumentException("The site could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnreadableDocumentException("The download was interrupted");
        }
    }

    private static byte[] read(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = body.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > KnowledgeLimits.MAX_WEB_PAGE_BYTES) {
                    throw new UnreadableDocumentException("The page is larger than %d MB"
                            .formatted(KnowledgeLimits.MAX_WEB_PAGE_BYTES / (1024 * 1024)));
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UnreadableDocumentException("The download was cut off");
        }
    }

    private static FetchedPage parse(WebAddress address, byte[] body, String contentType) {
        String charset = charsetOf(contentType);
        Document page;
        try {
            page = Jsoup.parse(new ByteArrayInputStream(body), charset, address.value());
        } catch (IOException | IllegalArgumentException e) {
            throw new UnreadableDocumentException("The page could not be read");
        }
        List<String> links = page.select("a[href]").stream()
                .map(link -> link.attr("abs:href"))
                .filter(href -> !href.isBlank())
                .toList();
        Element title = page.selectFirst("title");
        // Stored as received; the extractor reads the charset from the page's meta tag or falls back to UTF-8, so
        // a charset given only in the HTTP header is written into the page for it
        byte[] stored = charset == null ? body : withCharset(page);
        return new FetchedPage(address, stored, title == null ? null : title.text(), links);
    }

    private static String charsetOf(String contentType) {
        int index = contentType.indexOf("charset=");
        if (index < 0) {
            return null;
        }
        String value = contentType.substring(index + "charset=".length()).replace("\"", "").strip();
        int end = value.indexOf(';');
        return end < 0 ? value : value.substring(0, end).strip();
    }

    private static byte[] withCharset(Document page) {
        page.charset(StandardCharsets.UTF_8);
        return page.outerHtml().getBytes(StandardCharsets.UTF_8);
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException e) {
            // Nothing left to read anyway
        }
    }
}

package com.irvinsegura.supportchat.knowledge.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The address of a web page imported into the knowledge base, normalized so the same page is stored once: lower-case
 * scheme and host, no default port, no fragment and no query (queries mostly carry tracking parameters and would
 * otherwise turn one page into endless variants).
 * <p>
 * Only the shape is checked here; whether the host is on the public internet is decided where the page is fetched,
 * since a name can resolve differently later.
 */
public record WebAddress(String value) {

    private static final Pattern FILE_EXTENSION = Pattern.compile(
            ".*\\.(pdf|jpe?g|png|gif|webp|svg|ico|bmp|tiff?|mp[34]|m4a|mov|avi|webm|wav|zip|rar|gz|7z|tar|exe|dmg|apk"
                    + "|css|js|json|xml|rss|txt|csv|docx?|xlsx?|pptx?|woff2?|ttf|eot)$");

    public WebAddress {
        if (value == null || value.isBlank()) {
            throw new InvalidWebAddressException("Enter the page's address");
        }
    }

    /**
     * @throws InvalidWebAddressException when the text is not an http(s) address of a named or numbered host
     */
    public static WebAddress parse(String text) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            throw new InvalidWebAddressException("Enter the page's address");
        }
        if (raw.length() > KnowledgeLimits.MAX_URL_LENGTH) {
            throw new InvalidWebAddressException("The address is too long");
        }
        // People paste "misitio.com" as often as the full address
        if (!raw.contains("://")) {
            raw = "https://" + raw;
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidWebAddressException("The address is not valid");
        }
        return normalize(uri).orElseThrow(() -> new InvalidWebAddressException(
                "Use a public web address that starts with http:// or https://"));
    }

    /**
     * A link found on {@code base}, if it is another page of the same site worth importing: same host (ignoring a
     * leading "www."), http(s), and not a file such as an image or a PDF.
     */
    public Optional<WebAddress> sameSiteLink(String href) {
        if (href == null || href.isBlank() || href.length() > KnowledgeLimits.MAX_URL_LENGTH) {
            return Optional.empty();
        }
        try {
            URI resolved = URI.create(value).resolve(new URI(href.strip()));
            return normalize(resolved)
                    .filter(link -> siteOf(link.uri().getHost()).equals(siteOf(uri().getHost())))
                    .filter(link -> !FILE_EXTENSION.matcher(link.uri().getPath().toLowerCase(Locale.ROOT)).matches())
                    .filter(link -> !link.equals(this));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public URI uri() {
        return URI.create(value);
    }

    public String host() {
        return uri().getHost();
    }

    private static Optional<WebAddress> normalize(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }
        String host = uri.getHost();
        // Credentials in a link are either a mistake or an attempt to disguise the real host
        if (host == null || host.isBlank() || uri.getRawUserInfo() != null) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            port = -1;
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String normalized = scheme + "://" + host + (port == -1 ? "" : ":" + port) + path;
        if (normalized.length() > KnowledgeLimits.MAX_URL_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new WebAddress(normalized));
    }

    private static String siteOf(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    @Override
    public String toString() {
        return value;
    }
}

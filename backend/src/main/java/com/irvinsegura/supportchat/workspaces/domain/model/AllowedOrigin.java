package com.irvinsegura.supportchat.workspaces.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * A web origin (scheme, host and optional port) allowed to embed a workspace's widget. Browsers compare origins
 * exactly, so values are normalized: lowercase, no path, no default port. Plain HTTP is accepted only for local
 * development hosts, so a production site can never be allowed over an unencrypted connection.
 */
public record AllowedOrigin(String value) {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    public static AllowedOrigin parse(String input) {
        if (input == null || input.isBlank() || input.length() > 255) {
            throw invalid(input);
        }
        URI uri;
        try {
            uri = new URI(input.strip());
        } catch (URISyntaxException e) {
            throw invalid(input);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean hasPath = uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/");
        if (host.isEmpty() || hasPath || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw invalid(input);
        }
        boolean local = LOCAL_HOSTS.contains(host);
        if (!scheme.equals("https") && !(scheme.equals("http") && local)) {
            throw invalid(input);
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443)
                || (scheme.equals("http") && port == 80);
        return new AllowedOrigin(scheme + "://" + host + (defaultPort ? "" : ":" + port));
    }

    private static InvalidWorkspaceSettingsException invalid(String input) {
        return new InvalidWorkspaceSettingsException(
                "Not a valid origin: use https://host[:port], or http:// only for localhost");
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.irvinsegura.supportchat.knowledge.infrastructure.fetching;

import com.irvinsegura.supportchat.knowledge.domain.port.WebPageFetcher;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Factory for the web page fetcher, which stays package-private.
 */
public final class WebFetchers {

    private WebFetchers() {
    }

    /**
     * @param allowPrivateNetwork also fetch from private addresses and non-standard ports; development and tests only
     */
    public static WebPageFetcher http(Duration timeout, boolean allowPrivateNetwork) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // Redirects are followed by hand so each target is checked
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HttpWebPageFetcher(http, timeout, allowPrivateNetwork);
    }
}

package com.irvinsegura.supportchat.knowledge.domain.port;

import com.irvinsegura.supportchat.knowledge.domain.model.WebAddress;
import java.util.List;

/**
 * Downloads web pages imported into the knowledge base.
 */
public interface WebPageFetcher {

    /**
     * @throws com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException when the page cannot be
     *                                                                                        downloaded, is not
     *                                                                                        HTML, is too large or
     *                                                                                        lives on a private
     *                                                                                        network
     */
    FetchedPage fetch(WebAddress address);

    /**
     * @param address where the page was found after redirects
     * @param title   the page's title, or null
     * @param links   the addresses the page links to, as written (relative or absolute), in page order
     */
    record FetchedPage(WebAddress address, byte[] content, String title, List<String> links) {

        public FetchedPage {
            links = List.copyOf(links);
        }
    }
}

package com.irvinsegura.supportchat.workspaces;

import java.util.List;
import java.util.UUID;

/**
 * A workspace as seen from its public chat widget.
 *
 * @param allowedOrigins web origins allowed to embed the widget, e.g. {@code https://shop.example.com}
 */
public record WidgetWorkspace(UUID workspaceId, String botName, String welcomeMessage, List<String> allowedOrigins) {

    public WidgetWorkspace {
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}

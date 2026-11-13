package com.irvinsegura.supportchat.shared.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins of the operator panel allowed to call the API from a browser. Widget origins are set per workspace.
 */
@ConfigurationProperties("supportchat.web.cors")
public record PanelCorsProperties(List<String> allowedOrigins) {

    public PanelCorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
    }
}

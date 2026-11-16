package com.irvinsegura.supportchat.workspaces.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.workspaces.WidgetWorkspace;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import com.irvinsegura.supportchat.workspaces.domain.model.UnknownWidgetKeyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the widget needs to draw itself before the visitor writes anything. Public: the widget key is not a secret.
 */
@RestController
@RequestMapping("/api/v1/widget/{widgetKey}")
@Tag(name = "Widget")
class WidgetConfigController {

    private final Workspaces workspaces;
    private final WidgetTexts texts;

    WidgetConfigController(Workspaces workspaces, WidgetTexts texts) {
        this.workspaces = workspaces;
        this.texts = texts;
    }

    @GetMapping("/config")
    @Operation(summary = "Assistant name, greeting and, for browsers in another language, the widget's own texts")
    ResponseEntity<WidgetConfigResponse> config(@PathVariable String widgetKey,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        WidgetWorkspace workspace = workspaces.findByWidgetKey(widgetKey).orElseThrow(UnknownWidgetKeyException::new);
        Map<String, String> translated = Language.preferred(acceptLanguage).map(texts::in).orElse(Map.of());
        // Short browser cache: every page view loads the widget, and a changed greeting may wait a minute. The texts
        // depend on the browser's language, so shared caches must keep one copy per language
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .varyBy(HttpHeaders.ACCEPT_LANGUAGE)
                .body(new WidgetConfigResponse(workspace.botName(), workspace.welcomeMessage(), translated));
    }

    /**
     * @param texts the widget's texts in the browser's language, by key; left out when the widget already speaks it
     */
    record WidgetConfigResponse(String botName, String welcomeMessage,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> texts) {
    }
}

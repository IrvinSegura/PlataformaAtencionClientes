package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.AttentionService;
import com.irvinsegura.supportchat.conversations.application.AttentionService.AlertView;
import com.irvinsegura.supportchat.conversations.domain.model.AttentionSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What happens when a customer needs a person: whether an upset customer goes to the team and whom to alert by
 * WhatsApp, and the alerts raised lately. Owners and administrators only.
 */
@RestController
@RequestMapping("/api/v1/assistant/attention")
@Tag(name = "Assistant")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
class AttentionController {

    private final AttentionService attention;

    AttentionController(AttentionService attention) {
        this.attention = attention;
    }

    @GetMapping
    @Operation(summary = "The workspace's settings; the defaults until it saves its own")
    AttentionSettings settings() {
        return attention.settings();
    }

    @PutMapping
    @Operation(summary = "Change whom to alert and whether upset customers go to the team")
    AttentionSettings configure(@Valid @RequestBody SettingsRequest request) {
        return attention.configure(new AttentionSettings(request.handOffFrustrated(), request.alertsEnabled(),
                request.alertPhones(), request.alertOnRequest(), request.alertOnFrustration(), request.template(),
                request.templateLanguage(), request.alertOnDifficult() == null || request.alertOnDifficult(),
                request.alertOnUsage() == null || request.alertOnUsage()));
    }

    @GetMapping("/alerts")
    @Operation(summary = "The latest customers who asked for a person or seemed upset, and whether the alert arrived")
    List<AlertView> alerts() {
        return attention.recentAlerts();
    }

    /**
     * @param alertOnDifficult left out, as by clients older than the setting, it alerts
     * @param alertOnUsage     likewise
     */
    record SettingsRequest(boolean handOffFrustrated, boolean alertsEnabled,
            @NotNull @Size(max = AttentionSettings.MAX_PHONES) List<@Size(max = 32) String> alertPhones,
            boolean alertOnRequest, boolean alertOnFrustration, @Size(max = 512) String template,
            @Size(max = 16) String templateLanguage, Boolean alertOnDifficult, Boolean alertOnUsage) {
    }
}

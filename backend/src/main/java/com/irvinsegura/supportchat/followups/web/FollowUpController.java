package com.irvinsegura.supportchat.followups.web;

import com.irvinsegura.supportchat.followups.application.FollowUpService;
import com.irvinsegura.supportchat.followups.application.FollowUpService.FollowUpPageView;
import com.irvinsegura.supportchat.followups.application.FollowUpService.FollowUpView;
import com.irvinsegura.supportchat.followups.application.FollowUpService.TemplateInput;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpStatus;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate.WhatsAppTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Follow-ups the team schedules for customers who write through WhatsApp, email, Messenger or Instagram, and the
 * templates they write them from. Any member schedules and cancels them; owners and administrators keep the templates.
 */
@RestController
@Tag(name = "Follow-ups")
@SecurityRequirement(name = "bearerAuth")
class FollowUpController {

    private final FollowUpService followUps;

    FollowUpController(FollowUpService followUps) {
        this.followUps = followUps;
    }

    @GetMapping("/api/v1/follow-up-templates")
    @Operation(summary = "The workspace's follow-up templates, by name")
    List<FollowUpTemplate> templates() {
        return followUps.templates();
    }

    @PostMapping("/api/v1/follow-up-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Create a template; {nombre} in its text becomes the customer's name")
    FollowUpTemplate createTemplate(@Valid @RequestBody TemplateRequest request) {
        return followUps.createTemplate(request.input());
    }

    @PutMapping("/api/v1/follow-up-templates/{templateId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Change a template; follow-ups already scheduled from it keep their text")
    FollowUpTemplate updateTemplate(@PathVariable UUID templateId, @Valid @RequestBody TemplateRequest request) {
        return followUps.updateTemplate(templateId, request.input());
    }

    @DeleteMapping("/api/v1/follow-up-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Delete a template; follow-ups already scheduled from it are still sent")
    void deleteTemplate(@PathVariable UUID templateId) {
        followUps.deleteTemplate(templateId);
    }

    @GetMapping("/api/v1/follow-ups")
    @Operation(summary = "Follow-ups; scheduled ones soonest first, the rest most recent first")
    FollowUpPageView list(@RequestParam(required = false) FollowUpStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return followUps.page(status, page, size);
    }

    @GetMapping("/api/v1/conversations/{conversationId}/follow-ups")
    @Operation(summary = "The follow-ups scheduled from a conversation, soonest first")
    List<FollowUpView> ofConversation(@PathVariable UUID conversationId) {
        return followUps.ofConversation(conversationId);
    }

    @PostMapping("/api/v1/conversations/{conversationId}/follow-ups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a message to the customer of a channel conversation, from a template or a text, "
            + "up to 90 days ahead")
    FollowUpView schedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody ScheduleRequest request) {
        return followUps.schedule(conversationId, UUID.fromString(jwt.getSubject()), request.templateId(),
                request.text(), request.dueAt());
    }

    @PostMapping("/api/v1/follow-ups/{followUpId}/cancellation")
    @Operation(summary = "Call off a follow-up that was not sent yet")
    FollowUpView cancel(@PathVariable UUID followUpId) {
        return followUps.cancel(followUpId);
    }

    /**
     * @param whatsappTemplate      the approved WhatsApp template to send after 24 hours of silence, or empty
     * @param whatsappLanguage      its language, e.g. es_MX
     * @param whatsappNameParameter whether its {{1}} is the customer's name
     */
    record TemplateRequest(@NotNull @Size(max = FollowUpTemplate.MAX_NAME_LENGTH) String name,
            @NotNull @Size(max = FollowUpTemplate.MAX_BODY_LENGTH) String body,
            @Size(max = 512) String whatsappTemplate, @Size(max = 10) String whatsappLanguage,
            Boolean whatsappNameParameter) {

        TemplateInput input() {
            WhatsAppTemplate whatsapp = whatsappTemplate == null || whatsappTemplate.isBlank() ? null
                    : new WhatsAppTemplate(whatsappTemplate.strip(),
                            whatsappLanguage == null ? null : whatsappLanguage.strip(),
                            Boolean.TRUE.equals(whatsappNameParameter));
            return new TemplateInput(name, body, whatsapp);
        }
    }

    /**
     * @param templateId the template to write it from, or null to send {@code text}
     */
    record ScheduleRequest(UUID templateId, @Size(max = FollowUpTemplate.MAX_BODY_LENGTH) String text,
            @NotNull Instant dueAt) {
    }
}

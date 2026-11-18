package com.irvinsegura.supportchat.actions.web;

import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.ParameterType;
import com.irvinsegura.supportchat.actions.application.ActionService;
import com.irvinsegura.supportchat.actions.application.ActionService.ActionView;
import com.irvinsegura.supportchat.actions.domain.model.Action;
import com.irvinsegura.supportchat.actions.domain.port.SigningSecretRepository.SigningSecret;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The assistant's actions: webhooks of the business it may call. The whole team sees them; owners and administrators
 * change them.
 */
@RestController
@RequestMapping("/api/v1/actions")
@Tag(name = "Actions")
@SecurityRequirement(name = "bearerAuth")
class ActionController {

    private final ActionService actions;

    ActionController(ActionService actions) {
        this.actions = actions;
    }

    @GetMapping
    @Operation(summary = "The workspace's actions, by name")
    List<ActionView> list() {
        return actions.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Add an action; its endpoint must be a public https address")
    ActionView create(@Valid @RequestBody ActionRequest request) {
        return actions.create(request.details());
    }

    @PutMapping("/{actionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Change an action, or enable and disable it")
    ActionView update(@PathVariable UUID actionId, @Valid @RequestBody ActionRequest request) {
        return actions.update(actionId, request.details());
    }

    @DeleteMapping("/{actionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Remove an action; its past runs stay in the conversations")
    ResponseEntity<Void> delete(@PathVariable UUID actionId) {
        actions.delete(actionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/signing-secret")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "The secret webhooks use to verify the X-SupportChat-Signature header")
    ResponseEntity<SigningSecret> signingSecret() {
        return noStore(actions.signingSecret());
    }

    @PostMapping("/signing-secret/rotation")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Replace the signing secret; webhooks must switch to the new one")
    ResponseEntity<SigningSecret> rotateSigningSecret() {
        return noStore(actions.rotateSigningSecret());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    record ActionRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = Action.MAX_TITLE_LENGTH) String title,
            @NotBlank @Size(max = Action.MAX_DESCRIPTION_LENGTH) String description,
            @NotBlank @Size(max = Action.MAX_URL_LENGTH) String endpointUrl,
            @NotNull @Size(max = Action.MAX_PARAMETERS) List<@Valid @NotNull ParameterRequest> parameters,
            boolean requiresConfirmation,
            boolean enabled) {

        Action.Details details() {
            return new Action.Details(name, title, description, endpointUrl,
                    parameters.stream().map(ParameterRequest::parameter).toList(), requiresConfirmation, enabled);
        }
    }

    record ParameterRequest(
            @NotBlank @Size(max = 64) String name,
            @NotNull ParameterType type,
            @NotBlank @Size(max = Action.MAX_PARAMETER_DESCRIPTION_LENGTH) String description,
            boolean required,
            @Size(max = Action.MAX_PATTERN_LENGTH) String pattern) {

        ActionParameter parameter() {
            return new ActionParameter(name, type, description, required, pattern);
        }
    }
}

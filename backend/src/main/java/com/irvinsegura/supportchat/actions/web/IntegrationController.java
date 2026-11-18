package com.irvinsegura.supportchat.actions.web;

import com.irvinsegura.supportchat.actions.application.IntegrationService;
import com.irvinsegura.supportchat.actions.application.IntegrationService.IntegrationView;
import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationNotFoundException;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ready-made integrations (Shopify, Zendesk) that give the assistant actions once connected with a token. Owners and
 * administrators connect them; the token can be replaced but is never shown again.
 */
@RestController
@RequestMapping("/api/v1/actions/integrations")
@Tag(name = "Actions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
class IntegrationController {

    private final IntegrationService integrations;

    IntegrationController(IntegrationService integrations) {
        this.integrations = integrations;
    }

    @GetMapping
    @Operation(summary = "Every integration, connected or not, with the actions it gives the assistant")
    ResponseEntity<List<IntegrationView>> list() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(integrations.list());
    }

    @PutMapping("/{provider:shopify|zendesk}")
    @Operation(summary = "Connect an integration or change it; a blank token keeps the saved one")
    ResponseEntity<IntegrationView> configure(@PathVariable String provider,
            @Valid @RequestBody IntegrationRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(integrations.configure(provider(provider), new Integration.Settings(request.account(),
                        request.username(), request.token(), request.enabled())));
    }

    @DeleteMapping("/{provider:shopify|zendesk}")
    @Operation(summary = "Disconnect an integration; the assistant stops using its actions")
    ResponseEntity<Void> disconnect(@PathVariable String provider) {
        integrations.disconnect(provider(provider));
        return ResponseEntity.noContent().build();
    }

    private static IntegrationProvider provider(String pathName) {
        return IntegrationProvider.ofPathName(pathName).orElseThrow(IntegrationNotFoundException::new);
    }

    /**
     * @param account  the Shopify store (mitienda.myshopify.com) or the Zendesk subdomain
     * @param username the Zendesk agent's email address
     * @param token    blank keeps the saved one
     */
    record IntegrationRequest(@NotBlank @Size(max = 253) String account, @Size(max = 254) String username,
            @Size(max = 512) String token, boolean enabled) {

        @Override
        public String toString() {
            return "IntegrationRequest[account=" + account + ", enabled=" + enabled + "]";
        }
    }
}

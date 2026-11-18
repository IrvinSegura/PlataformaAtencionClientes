package com.irvinsegura.supportchat.platform.web;

import com.irvinsegura.supportchat.platform.application.PlatformAdmins;
import com.irvinsegura.supportchat.platform.application.PlatformService;
import com.irvinsegura.supportchat.platform.application.PlatformWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of the platform, for the users configured to run it. Anyone else gets 403, whatever their role in
 * their own workspace.
 */
@RestController
@Validated
@RequestMapping("/api/v1/platform")
class PlatformController {

    private static final String ADMINS_ONLY = "@platformAdmins.allows(authentication)";

    private final PlatformService platform;
    private final PlatformAdmins admins;

    PlatformController(PlatformService platform, PlatformAdmins admins) {
        this.platform = platform;
        this.admins = admins;
    }

    /**
     * Whether the operator runs the platform, so the panel knows whether to show its administration.
     */
    @GetMapping("/me")
    PlatformAccess me(@AuthenticationPrincipal Jwt jwt) {
        return new PlatformAccess(admins.isAdmin(userIdOf(jwt)));
    }

    @GetMapping("/workspaces")
    @PreAuthorize(ADMINS_ONLY)
    PlatformService.PlatformWorkspacePage workspaces(@RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return platform.page(query, page, size);
    }

    @GetMapping("/workspaces/{id}")
    @PreAuthorize(ADMINS_ONLY)
    PlatformWorkspace workspace(@PathVariable UUID id) {
        return platform.get(id);
    }

    @PostMapping("/workspaces/{id}/suspension")
    @PreAuthorize(ADMINS_ONLY)
    PlatformWorkspace suspend(@PathVariable UUID id, @Valid @RequestBody SuspensionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return platform.suspend(id, request.reason(), userIdOf(jwt));
    }

    @DeleteMapping("/workspaces/{id}/suspension")
    @PreAuthorize(ADMINS_ONLY)
    PlatformWorkspace reinstate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return platform.reinstate(id, userIdOf(jwt));
    }

    private static UUID userIdOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record PlatformAccess(boolean platformAdmin) {
    }

    record SuspensionRequest(@NotBlank @Size(max = 500) String reason) {
    }
}

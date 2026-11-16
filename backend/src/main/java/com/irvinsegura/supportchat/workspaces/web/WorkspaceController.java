package com.irvinsegura.supportchat.workspaces.web;

import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.workspaces.application.WorkspaceService;
import com.irvinsegura.supportchat.workspaces.application.WorkspaceView;
import com.irvinsegura.supportchat.workspaces.domain.model.Workspace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in operator's workspace. The workspace always comes from the access token, never from the request,
 * so there is no id an operator could change to reach someone else's.
 */
@RestController
@RequestMapping("/api/v1/workspace")
@Tag(name = "Workspace")
@SecurityRequirement(name = "bearerAuth")
class WorkspaceController {

    private final WorkspaceService workspaces;

    WorkspaceController(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @GetMapping
    @Operation(summary = "Current workspace and its widget settings")
    WorkspaceView get() {
        return workspaces.get(TenantContext.requireWorkspaceId());
    }

    @PutMapping("/widget-settings")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Change the assistant name, greeting and the sites allowed to embed the widget")
    WorkspaceView changeWidgetSettings(@Valid @RequestBody WidgetSettingsRequest request) {
        return workspaces.changeWidgetSettings(TenantContext.requireWorkspaceId(), request.botName(),
                request.welcomeMessage(), request.allowedOrigins());
    }

    @PostMapping("/widget-key/rotation")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Replace the widget key; sites with the old key stop working")
    WorkspaceView rotateWidgetKey() {
        return workspaces.rotateWidgetKey(TenantContext.requireWorkspaceId());
    }

    record WidgetSettingsRequest(
            @NotBlank @Size(max = Workspace.MAX_BOT_NAME_LENGTH) String botName,
            @NotBlank @Size(max = Workspace.MAX_WELCOME_LENGTH) String welcomeMessage,
            @NotNull @Size(max = Workspace.MAX_ALLOWED_ORIGINS) List<@NotBlank @Size(max = 255) String> allowedOrigins) {
    }
}

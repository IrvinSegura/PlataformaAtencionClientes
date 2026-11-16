package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.application.InvitationService;
import com.irvinsegura.supportchat.identity.application.InvitationService.InvitationView;
import com.irvinsegura.supportchat.identity.application.InvitationService.IssuedInvitation;
import com.irvinsegura.supportchat.identity.application.MemberService;
import com.irvinsegura.supportchat.identity.application.MemberService.MemberView;
import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.workspaces.WorkspaceMembership;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The team of the signed-in operator's workspace: members and pending invitations.
 */
@RestController
@Tag(name = "Team")
@SecurityRequirement(name = "bearerAuth")
class MemberController {

    private final MemberService members;
    private final InvitationService invitations;

    MemberController(MemberService members, InvitationService invitations) {
        this.members = members;
        this.invitations = invitations;
    }

    @GetMapping("/api/v1/workspace/members")
    @Operation(summary = "Members of the workspace")
    List<MemberView> members(@AuthenticationPrincipal Jwt jwt) {
        return members.members(CurrentOperator.of(jwt).workspaceId());
    }

    @PutMapping("/api/v1/workspace/members/{userId}/role")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Change a member's role between administrator and agent")
    ResponseEntity<Void> changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
            @Valid @RequestBody RoleRequest request) {
        CurrentOperator operator = CurrentOperator.of(jwt);
        members.changeRole(operator.workspaceId(), operator.role(), userId, request.role());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/workspace/members/{userId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Remove a member; the owner cannot be removed")
    ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        CurrentOperator operator = CurrentOperator.of(jwt);
        members.remove(operator.workspaceId(), operator.userId(), operator.role(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/workspace/invitations")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Invitations that can still be accepted")
    List<InvitationView> pending(@AuthenticationPrincipal Jwt jwt) {
        return invitations.pending(CurrentOperator.of(jwt).workspaceId());
    }

    @PostMapping("/api/v1/workspace/invitations")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Invite someone by email; the response carries the link token, shown only this once")
    ResponseEntity<IssuedInvitation> invite(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvitationRequest request) {
        CurrentOperator operator = CurrentOperator.of(jwt);
        IssuedInvitation issued = invitations.invite(operator.workspaceId(), operator.userId(), operator.role(),
                request.email(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(issued);
    }

    @DeleteMapping("/api/v1/workspace/invitations/{invitationId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Revoke an invitation so its link stops working")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invitationId) {
        invitations.revoke(CurrentOperator.of(jwt).workspaceId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/me/workspaces")
    @Operation(summary = "Every workspace the signed-in operator belongs to")
    List<WorkspaceMembership> workspaces(@AuthenticationPrincipal Jwt jwt) {
        return members.workspacesOf(UUID.fromString(jwt.getSubject()));
    }

    record RoleRequest(@NotNull WorkspaceRole role) {
    }

    record InvitationRequest(@NotBlank @Size(max = Email.MAX_LENGTH) String email, @NotNull WorkspaceRole role) {
    }
}

package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.application.InvitationService;
import com.irvinsegura.supportchat.identity.application.InvitationService.InvitationPreview;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.web.SessionResponses.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening an invitation link, without being signed in. The token goes in the body, never in the URL, so it stays
 * out of access logs; the panel reads it from the link's fragment, which browsers never send.
 */
@RestController
@Tag(name = "Invitations")
class InvitationController {

    private final InvitationService invitations;
    private final SessionResponses responses;

    InvitationController(InvitationService invitations, SessionResponses responses) {
        this.invitations = invitations;
        this.responses = responses;
    }

    @PostMapping("/api/v1/invitations/preview")
    @Operation(summary = "Workspace, email and role of an invitation that can still be accepted")
    InvitationPreview preview(@Valid @RequestBody PreviewRequest request) {
        return invitations.preview(request.token());
    }

    @PostMapping("/api/v1/invitations/acceptance")
    @Operation(summary = "Join the workspace (with the existing password, or creating the account) and sign in")
    ResponseEntity<SessionResponse> accept(@Valid @RequestBody AcceptRequest request) {
        return responses.of(HttpStatus.OK,
                invitations.accept(request.token(), request.displayName(), request.password(),
                        Boolean.TRUE.equals(request.acceptTerms())));
    }

    record PreviewRequest(@NotBlank @Size(max = 100) String token) {

        @Override
        public String toString() {
            return "PreviewRequest[]";
        }
    }

    /**
     * @param displayName required only when the invited email has no account yet
     * @param acceptTerms required only when the invited email has no account yet
     */
    record AcceptRequest(
            @NotBlank @Size(max = 100) String token,
            @Size(max = User.MAX_DISPLAY_NAME_LENGTH) String displayName,
            @NotBlank @Size(max = 256) String password,
            Boolean acceptTerms) {

        @Override
        public String toString() {
            return "AcceptRequest[]";
        }
    }
}

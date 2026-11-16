package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.application.AuthSession;
import com.irvinsegura.supportchat.identity.application.AuthenticationService;
import com.irvinsegura.supportchat.identity.application.ProfileService;
import com.irvinsegura.supportchat.identity.application.ProfileService.OperatorProfile;
import com.irvinsegura.supportchat.identity.application.RegistrationService;
import com.irvinsegura.supportchat.identity.application.RegistrationService.RegisterCommand;
import com.irvinsegura.supportchat.identity.application.TermsService;
import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.PasswordPolicy;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.web.SessionResponses.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Authentication")
class AuthController {

    static final String CSRF_HEADER = SessionResponses.CSRF_HEADER;

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final ProfileService profiles;
    private final TermsService terms;
    private final SessionResponses responses;

    AuthController(RegistrationService registration, AuthenticationService authentication, ProfileService profiles,
            TermsService terms, SessionResponses responses) {
        this.registration = registration;
        this.authentication = authentication;
        this.profiles = profiles;
        this.terms = terms;
        this.responses = responses;
    }

    @PostMapping("/api/v1/auth/register")
    @Operation(summary = "Create an operator account and its workspace, and sign in")
    ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthSession session = registration.register(new RegisterCommand(request.email(), request.password(),
                request.displayName(), request.workspaceName(), Boolean.TRUE.equals(request.acceptTerms())));
        return responses.of(HttpStatus.CREATED, session);
    }

    @PostMapping("/api/v1/auth/login")
    @Operation(summary = "Sign in with email and password")
    ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return responses.of(HttpStatus.OK, authentication.login(request.email(), request.password()));
    }

    @PostMapping("/api/v1/auth/refresh")
    @Operation(summary = "Trade the refresh token cookie for a new access token (and a new cookie)")
    ResponseEntity<SessionResponse> refresh(@RequestHeader(CSRF_HEADER) String csrf, HttpServletRequest request) {
        return responses.of(HttpStatus.OK, authentication.refresh(responses.refreshTokenFrom(request)));
    }

    @PostMapping("/api/v1/auth/logout")
    @Operation(summary = "End the login the refresh token cookie belongs to")
    ResponseEntity<Void> logout(@RequestHeader(CSRF_HEADER) String csrf, HttpServletRequest request) {
        authentication.logout(responses.refreshTokenFrom(request));
        return responses.cleared();
    }

    @PostMapping("/api/v1/auth/workspace-switch")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign in to another workspace the operator belongs to; the current login ends")
    ResponseEntity<SessionResponse> switchWorkspace(@RequestHeader(CSRF_HEADER) String csrf,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkspaceSwitchRequest body,
            HttpServletRequest request) {
        return responses.of(HttpStatus.OK, authentication.switchWorkspace(UUID.fromString(jwt.getSubject()),
                body.workspaceId(), responses.refreshTokenFrom(request)));
    }

    @GetMapping("/api/v1/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The signed-in operator")
    OperatorProfile me(@AuthenticationPrincipal Jwt jwt) {
        return profiles.profile(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/api/v1/me/terms-acceptance")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Accept the terms of service in force, naming the version that was shown")
    OperatorProfile acceptTerms(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TermsRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        terms.accept(userId, request.version());
        return profiles.profile(userId);
    }

    record RegisterRequest(
            @NotBlank @Size(max = Email.MAX_LENGTH) String email,
            // Checked in full by PasswordPolicy; the bound only stops absurd payloads early
            @NotBlank @Size(max = PasswordPolicy.MAX_BYTES) String password,
            @NotBlank @Size(max = User.MAX_DISPLAY_NAME_LENGTH) String displayName,
            @NotBlank @Size(max = 80) String workspaceName,
            // Checked by the service, which answers TERMS_NOT_ACCEPTED instead of a bare validation error
            Boolean acceptTerms) {

        @Override
        public String toString() {
            return "RegisterRequest[email=%s]".formatted(email);
        }
    }

    record LoginRequest(
            @NotBlank @Size(max = Email.MAX_LENGTH) String email,
            @NotBlank @Size(max = 256) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=%s]".formatted(email);
        }
    }

    record WorkspaceSwitchRequest(@NotNull UUID workspaceId) {
    }

    record TermsRequest(@NotBlank @Size(max = 64) String version) {
    }
}

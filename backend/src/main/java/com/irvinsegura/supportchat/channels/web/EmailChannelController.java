package com.irvinsegura.supportchat.channels.web;

import com.irvinsegura.supportchat.channels.application.EmailChannelService;
import com.irvinsegura.supportchat.channels.application.EmailChannelService.EmailChannelView;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel.Settings;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannelNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's support mailbox. Owners and administrators connect it; the password can be replaced but is never
 * shown again.
 */
@RestController
@RequestMapping("/api/v1/channels/email")
@Tag(name = "Channels")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
class EmailChannelController {

    private final EmailChannelService channels;

    EmailChannelController(EmailChannelService channels) {
        this.channels = channels;
    }

    @GetMapping
    @Operation(summary = "The connected mailbox, with when it was last read and why the last read failed")
    ResponseEntity<EmailChannelView> view() {
        return noStore(channels.view().orElseThrow(EmailChannelNotFoundException::new));
    }

    @PutMapping
    @Operation(summary = "Connect the mailbox or change it; a blank password keeps the saved one")
    ResponseEntity<EmailChannelView> configure(@Valid @RequestBody EmailChannelRequest request) {
        return noStore(channels.configure(new Settings(request.address(), request.displayName(), request.imapHost(),
                request.imapPort(), request.smtpHost(), request.smtpPort(), request.username(), request.password(),
                request.enabled())));
    }

    @DeleteMapping
    @Operation(summary = "Disconnect the mailbox; its conversations stay")
    ResponseEntity<Void> disconnect() {
        channels.disconnect();
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    record EmailChannelRequest(@NotBlank @Size(max = 254) String address, @Size(max = 100) String displayName,
            @NotBlank @Size(max = 253) String imapHost, @Min(1) @Max(65_535) int imapPort,
            @NotBlank @Size(max = 253) String smtpHost, @Min(1) @Max(65_535) int smtpPort,
            @Size(max = 254) String username, @Size(max = 512) String password, boolean enabled) {

        @Override
        public String toString() {
            return "EmailChannelRequest[address=" + address + ", imapHost=" + imapHost + ", smtpHost=" + smtpHost
                    + ", enabled=" + enabled + "]";
        }
    }
}

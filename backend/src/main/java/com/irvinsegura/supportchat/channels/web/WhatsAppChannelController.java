package com.irvinsegura.supportchat.channels.web;

import com.irvinsegura.supportchat.channels.application.WhatsAppChannelService;
import com.irvinsegura.supportchat.channels.application.WhatsAppChannelService.WhatsAppChannelView;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel.Settings;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannelNotFoundException;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * The workspace's WhatsApp Business number. Owners and administrators connect it; the access token and the app
 * secret can be replaced but are never shown again.
 */
@RestController
@RequestMapping("/api/v1/channels/whatsapp")
@Tag(name = "Channels")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
class WhatsAppChannelController {

    private final WhatsAppChannelService channels;

    WhatsAppChannelController(WhatsAppChannelService channels) {
        this.channels = channels;
    }

    @GetMapping
    @Operation(summary = "The connected number, with the webhook path and verify token to set up in Meta")
    ResponseEntity<WhatsAppChannelView> view() {
        return noStore(channels.view().orElseThrow(WhatsAppChannelNotFoundException::new));
    }

    @PutMapping
    @Operation(summary = "Connect the number or change it; blank secrets keep the saved ones")
    ResponseEntity<WhatsAppChannelView> configure(@Valid @RequestBody WhatsAppChannelRequest request) {
        return noStore(channels.configure(new Settings(request.phoneNumberId(), request.displayPhoneNumber(),
                request.accessToken(), request.appSecret(), request.enabled(), request.model())));
    }

    @DeleteMapping
    @Operation(summary = "Disconnect the number; its conversations stay")
    ResponseEntity<Void> disconnect() {
        channels.disconnect();
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    record WhatsAppChannelRequest(@NotBlank @Size(max = 30) String phoneNumberId,
            @Size(max = 32) String displayPhoneNumber, @Size(max = 2048) String accessToken, @Size(max = 256) String appSecret, boolean enabled,
            ModelPreference model) {

        @Override
        public String toString() {
            return "WhatsAppChannelRequest[phoneNumberId=" + phoneNumberId + ", enabled=" + enabled + ", model="
                    + model + "]";
        }
    }
}

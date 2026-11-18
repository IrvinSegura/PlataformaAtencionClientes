package com.irvinsegura.supportchat.channels.web;

import com.irvinsegura.supportchat.channels.application.MetaChannelService;
import com.irvinsegura.supportchat.channels.application.MetaChannelService.MetaChannelView;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannel.Settings;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannelNotFoundException;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's Facebook Page (Messenger) and Instagram account. Owners and administrators connect them; the
 * access token and the app secret can be replaced but are never shown again.
 */
@RestController
@RequestMapping("/api/v1/channels/{platform:messenger|instagram}")
@Tag(name = "Channels")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
class MetaChannelController {

    private final MetaChannelService channels;

    MetaChannelController(MetaChannelService channels) {
        this.channels = channels;
    }

    @GetMapping
    @Operation(summary = "The connected account, with the webhook path and verify token to set up in the Meta app")
    ResponseEntity<MetaChannelView> view(@PathVariable String platform) {
        return noStore(channels.view(platform(platform)).orElseThrow(MetaChannelNotFoundException::new));
    }

    @PutMapping
    @Operation(summary = "Connect the account or change it; blank secrets keep the saved ones")
    ResponseEntity<MetaChannelView> configure(@PathVariable String platform,
            @Valid @RequestBody MetaChannelRequest request) {
        return noStore(channels.configure(platform(platform), new Settings(request.accountId(),
                request.displayName(), request.accessToken(), request.appSecret(), request.enabled())));
    }

    @DeleteMapping
    @Operation(summary = "Disconnect the account; its conversations stay")
    ResponseEntity<Void> disconnect(@PathVariable String platform) {
        channels.disconnect(platform(platform));
        return ResponseEntity.noContent().build();
    }

    static MetaPlatform platform(String pathName) {
        return MetaPlatform.ofPathName(pathName).orElseThrow(MetaChannelNotFoundException::new);
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    /**
     * @param accountId the Page ID (Messenger) or the Instagram professional account ID
     */
    record MetaChannelRequest(@NotBlank @Size(max = 30) String accountId, @Size(max = 100) String displayName,
            @Size(max = 2048) String accessToken, @Size(max = 256) String appSecret, boolean enabled) {

        @Override
        public String toString() {
            return "MetaChannelRequest[accountId=" + accountId + ", enabled=" + enabled + "]";
        }
    }
}

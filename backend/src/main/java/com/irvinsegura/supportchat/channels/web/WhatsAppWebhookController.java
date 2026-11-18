package com.irvinsegura.supportchat.channels.web;

import com.irvinsegura.supportchat.channels.application.WhatsAppChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Meta delivers a connected number's messages. Public: the channel key in the path selects the workspace, and
 * Meta's signature over the body proves where a delivery comes from.
 */
@RestController
@RequestMapping("/api/v1/channels/whatsapp/{channelKey}/webhook")
@Tag(name = "Channels")
class WhatsAppWebhookController {

    static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppChannelService channels;

    WhatsAppWebhookController(WhatsAppChannelService channels) {
        this.channels = channels;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Meta's subscription check: answers the challenge when the verify token matches")
    ResponseEntity<String> verify(@PathVariable String channelKey,
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        return ResponseEntity.ok(channels.verifySubscription(channelKey, mode, verifyToken, challenge));
    }

    /**
     * The body is read as bytes: the signature covers them exactly, before any parsing.
     */
    @PostMapping(consumes = MediaType.ALL_VALUE)
    @Operation(summary = "A delivery of messages, signed with the app secret in X-Hub-Signature-256")
    ResponseEntity<Void> receive(@PathVariable String channelKey, @RequestBody byte[] body,
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature) {
        channels.receive(channelKey, body, signature);
        return ResponseEntity.ok().build();
    }
}

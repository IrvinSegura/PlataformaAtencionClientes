package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.ConversationIntent;
import com.irvinsegura.supportchat.conversations.application.ConversationService;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationDetail;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationPageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationSummary;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.OperatorMessageView;
import com.irvinsegura.supportchat.conversations.application.HandoffService;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationHandling;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.identity.OperatorClaims;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conversations of the operator's workspace, with each answer's sources and cost, and the actions to answer a visitor
 * in person. Any member may read them and take one over.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations")
@SecurityRequirement(name = "bearerAuth")
class OperatorConversationController {

    private final ConversationService conversations;
    private final HandoffService handoff;
    private final LiveConversationHub live;

    OperatorConversationController(ConversationService conversations, HandoffService handoff,
            LiveConversationHub live) {
        this.conversations = conversations;
        this.handoff = handoff;
        this.live = live;
    }

    @GetMapping
    @Operation(summary = "Conversations, most recently active first; with handling, only open ones handled that way "
            + "(WAITING lists the queue, oldest request first)")
    ConversationPageView list(@RequestParam(required = false) ConversationHandling handling,
            @RequestParam(required = false) ConversationIntent intent,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return conversations.page(handling, intent, page, size);
    }

    @PutMapping("/{conversationId}/intents")
    @Operation(summary = "Set what the conversation is about, correcting the automatic tags; later messages add none")
    ConversationSummary setIntents(@PathVariable UUID conversationId, @Valid @RequestBody IntentsRequest request) {
        return conversations.setIntents(conversationId, request.intents());
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "One conversation with all its messages")
    ConversationDetail get(@PathVariable UUID conversationId) {
        return conversations.detail(conversationId);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live changes of the workspace's conversations as server-sent events (conversation, "
            + "message); the stream ends after 15 minutes and the client reconnects")
    SseEmitter events() {
        return live.subscribeOperator(TenantContext.requireWorkspaceId());
    }

    @PostMapping("/{conversationId}/takeover")
    @Operation(summary = "Start answering the visitor in person; the assistant stops answering")
    ConversationSummary takeOver(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return handoff.takeOver(conversationId, memberId(jwt));
    }

    @PostMapping("/{conversationId}/release")
    @Operation(summary = "Hand the conversation back to the assistant")
    ConversationSummary release(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return handoff.release(conversationId, memberId(jwt), role(jwt));
    }

    @PostMapping("/{conversationId}/closure")
    @Operation(summary = "End the conversation; the visitor can start a new one")
    ConversationSummary close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return handoff.close(conversationId, memberId(jwt), role(jwt));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reply to the visitor; only the member who took the conversation over may")
    OperatorMessageView reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody ReplyRequest request) {
        return handoff.reply(conversationId, memberId(jwt), request.text());
    }

    private static UUID memberId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static WorkspaceRole role(Jwt jwt) {
        return WorkspaceRole.valueOf(jwt.getClaimAsStringList(OperatorClaims.ROLES).getFirst());
    }

    record IntentsRequest(@NotNull @Size(max = 20) Set<@NotNull ConversationIntent> intents) {
    }

    record ReplyRequest(@NotBlank @Size(max = Message.MAX_REPLY_LENGTH) String text) {
    }
}

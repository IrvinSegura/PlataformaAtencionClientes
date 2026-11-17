package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.ActionResponseService;
import com.irvinsegura.supportchat.conversations.application.AnswerService;
import com.irvinsegura.supportchat.conversations.application.AnswerService.PreparedQuestion;
import com.irvinsegura.supportchat.conversations.application.ConversationService;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.MessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.StartedConversation;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.VisitorConversationView;
import com.irvinsegura.supportchat.conversations.application.FeedbackService;
import com.irvinsegura.supportchat.conversations.application.HandoffService;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationRating;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.workspaces.WidgetHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public API the embeddable widget talks to. The widget key in the path selects the workspace (bound by the
 * widget tenant filter before this runs); the conversation token proves the visitor owns the conversation.
 */
@RestController
@RequestMapping("/api/v1/widget/{widgetKey}/conversations")
@Tag(name = "Widget")
class WidgetConversationController {

    private final ConversationService conversations;
    private final AnswerService answers;
    private final HandoffService handoff;
    private final ActionResponseService actionResponses;
    private final FeedbackService feedback;
    private final LiveConversationHub live;
    private final JsonMapper json;

    WidgetConversationController(ConversationService conversations, AnswerService answers, HandoffService handoff,
            ActionResponseService actionResponses, FeedbackService feedback, LiveConversationHub live,
            JsonMapper json) {
        this.conversations = conversations;
        this.answers = answers;
        this.handoff = handoff;
        this.actionResponses = actionResponses;
        this.feedback = feedback;
        this.live = live;
        this.json = json;
    }

    @PostMapping
    @Operation(summary = "Start a conversation; the visitor token in the response is shown only once")
    ResponseEntity<StartedConversation> start(@PathVariable String widgetKey,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        // Browsers send their language with every request: the platform's own texts start in it
        StartedConversation started = conversations.start(origin, Language.preferred(acceptLanguage).orElse(null));
        return ResponseEntity.created(URI.create(
                        "/api/v1/widget/" + widgetKey + "/conversations/" + started.conversationId()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(started);
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "The visitor's conversation so far, e.g. to redraw the widget after a reload")
    ResponseEntity<List<MessageView>> history(@PathVariable UUID conversationId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(conversations.visitorHistory(conversationId, visitorToken));
    }

    /**
     * Validation errors, a wrong token or a reached limit are answered as normal JSON errors, because the question
     * is checked before the first byte of the stream. Once streaming starts the status is 200 and any failure is
     * reported as a polite message inside the stream.
     */
    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask a question; the answer streams back as server-sent events (delta, then done)")
    void ask(@PathVariable UUID conversationId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken,
            @Valid @RequestBody QuestionRequest request, HttpServletResponse response) throws IOException {
        PreparedQuestion question = answers.prepare(conversationId, visitorToken, request.question());

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store");
        // Stops reverse proxies such as nginx from buffering the stream until the answer is complete
        response.setHeader("X-Accel-Buffering", "no");
        response.flushBuffer();

        SseAnswerSink sink = new SseAnswerSink(response.getOutputStream(), json);
        if (question.forwarded()) {
            // A person answers; the reply arrives later through the events stream
            sink.forwarded(question.messageId());
            return;
        }
        answers.answer(question, sink);
    }

    @GetMapping(value = "/{conversationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live state and replies from the team as server-sent events (state first, then state and "
            + "message); the stream ends after 30 minutes and the widget reconnects")
    SseEmitter events(@PathVariable UUID conversationId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken) {
        return live.subscribeVisitor(handoff.visitorState(conversationId, visitorToken));
    }

    @PostMapping("/{conversationId}/handoff")
    @Operation(summary = "Ask for a person; the assistant stops answering until someone takes over or the visitor "
            + "cancels")
    ResponseEntity<VisitorConversationView> requestHandoff(@PathVariable UUID conversationId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken) {
        return noStore(handoff.requestHandoff(conversationId, visitorToken));
    }

    @PostMapping("/{conversationId}/handoff/cancellation")
    @Operation(summary = "Stop waiting for a person and go back to the assistant")
    ResponseEntity<VisitorConversationView> cancelHandoff(@PathVariable UUID conversationId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken) {
        return noStore(handoff.cancelHandoff(conversationId, visitorToken));
    }

    @PostMapping("/{conversationId}/actions/{runId}/confirmation")
    @Operation(summary = "Confirm a change the assistant proposed; it runs now and the assistant's reply is returned")
    ResponseEntity<MessageView> confirmAction(@PathVariable UUID conversationId, @PathVariable UUID runId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken) {
        return noStore(actionResponses.respondAsVisitor(conversationId, visitorToken, runId, true));
    }

    @PostMapping("/{conversationId}/actions/{runId}/cancellation")
    @Operation(summary = "Reject a change the assistant proposed; nothing is sent to the business")
    ResponseEntity<MessageView> cancelAction(@PathVariable UUID conversationId, @PathVariable UUID runId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken) {
        return noStore(actionResponses.respondAsVisitor(conversationId, visitorToken, runId, false));
    }

    @PutMapping("/{conversationId}/messages/{messageId}/feedback")
    @Operation(summary = "Say whether an answer of the assistant helped; giving it again replaces it")
    ResponseEntity<Void> judgeAnswer(@PathVariable UUID conversationId, @PathVariable UUID messageId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken,
            @Valid @RequestBody AnswerFeedbackRequest request) {
        feedback.judgeAnswer(conversationId, visitorToken, messageId, request.helpful());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{conversationId}/rating")
    @Operation(summary = "Rate the conversation from 1 to 5, with an optional comment; rating again replaces it")
    ResponseEntity<Void> rate(@PathVariable UUID conversationId,
            @RequestHeader(WidgetHeaders.CONVERSATION_TOKEN) String visitorToken,
            @Valid @RequestBody RatingRequest request) {
        feedback.rate(conversationId, visitorToken, request.score(), request.comment());
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    record QuestionRequest(@NotBlank @Size(max = Message.MAX_QUESTION_LENGTH) String question) {
    }

    record AnswerFeedbackRequest(@NotNull Boolean helpful) {
    }

    record RatingRequest(@NotNull @Min(1) @Max(5) Integer score,
            @Size(max = ConversationRating.MAX_COMMENT_LENGTH) String comment) {
    }
}

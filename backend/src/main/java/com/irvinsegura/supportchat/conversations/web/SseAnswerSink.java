package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.AnswerSink;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.PendingActionView;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the answer as server-sent events straight to the response, flushing every piece so the visitor sees the
 * answer as it is written. Events: {@code delta} with a piece of text, then one {@code done} with the outcome, the
 * citations and, when the answer proposes a change, the action the visitor must confirm.
 */
final class SseAnswerSink implements AnswerSink {

    private final OutputStream out;
    private final JsonMapper json;

    SseAnswerSink(OutputStream out, JsonMapper json) {
        this.out = out;
        this.json = json;
    }

    @Override
    public void text(String delta) {
        send("delta", Map.of("text", delta));
    }

    @Override
    public void completed(UUID messageId, AnswerOutcome outcome, List<Citation> citations,
            PendingActionView pendingAction) {
        send("done", new Done(messageId, outcome, citations, pendingAction));
    }

    /**
     * The message was kept for the team instead of being answered: the only event of the stream.
     */
    void forwarded(UUID messageId) {
        send("forwarded", Map.of("messageId", messageId));
    }

    private void send(String event, Object data) {
        // Compact JSON never contains a line break, so one data line always holds the whole payload
        String frame = "event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n";
        try {
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new VisitorGoneException(e);
        }
    }

    record Done(UUID messageId, AnswerOutcome outcome, List<Citation> citations, PendingActionView pendingAction) {
    }
}

package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.conversations.application.AnswerSink.VisitorGoneException;
import com.irvinsegura.supportchat.conversations.application.ModelChoices;
import com.irvinsegura.supportchat.conversations.application.ModelChoices.ModelChoiceView;
import com.irvinsegura.supportchat.conversations.application.PlaygroundService;
import com.irvinsegura.supportchat.conversations.application.PlaygroundService.HistoryTurn;
import com.irvinsegura.supportchat.conversations.application.PlaygroundService.PlaygroundQuestion;
import com.irvinsegura.supportchat.conversations.application.PlaygroundService.PlaygroundResult;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where the team tries the assistant before customers see it, and the models a channel can answer with.
 */
@RestController
@Tag(name = "Assistant")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
class PlaygroundController {

    private static final Logger log = LoggerFactory.getLogger(PlaygroundController.class);
    static final int MAX_HISTORY = 20;

    private final PlaygroundService playground;
    private final ModelChoices models;
    private final JsonMapper json;

    PlaygroundController(PlaygroundService playground, ModelChoices models, JsonMapper json) {
        this.playground = playground;
        this.models = models;
        this.json = json;
    }

    @GetMapping("/api/v1/assistant/models")
    @Operation(summary = "The models a channel or the playground can answer with, and the Claude models behind each")
    List<ModelChoiceView> models() {
        return models.available();
    }

    /**
     * A wrong question or a used-up plan are normal JSON errors, checked before the stream starts. Then the answer
     * streams as {@code delta} events and one {@code done} event carries the inspector.
     */
    @PostMapping(value = "/api/v1/playground/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask the assistant as a customer would; the answer streams as server-sent events (delta, "
            + "then done with the passages found, the actions it would run, tokens and estimated cost). Nothing is "
            + "saved and no action runs")
    void answer(@Valid @RequestBody PlaygroundRequest request, HttpServletResponse response) throws IOException {
        List<HistoryTurn> history = request.history() == null ? List.of()
                : request.history().stream().map(turn -> new HistoryTurn(turn.fromCustomer(), turn.text())).toList();
        PlaygroundQuestion question = playground.prepare(request.question(), history, request.model());

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        response.flushBuffer();

        OutputStream out = response.getOutputStream();
        try {
            PlaygroundResult result = playground.answer(question, delta -> send(out, "delta", Map.of("text", delta)));
            send(out, "done", result);
        } catch (VisitorGoneException e) {
            log.debug("The tester left the playground before the answer ended");
        }
    }

    private void send(OutputStream out, String event, Object data) {
        String frame = "event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n";
        try {
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new VisitorGoneException(e);
        }
    }

    record PlaygroundRequest(@NotBlank @Size(max = Message.MAX_QUESTION_LENGTH) String question,
            @Size(max = MAX_HISTORY) List<@Valid @NotNull Turn> history, ModelPreference model) {
    }

    /**
     * @param fromCustomer true for what the tester wrote, false for what the assistant answered
     */
    record Turn(boolean fromCustomer, @Size(max = 8_000) String text) {
    }
}

package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.DifficultQuestionService;
import com.irvinsegura.supportchat.conversations.application.DifficultQuestionService.DifficultQuestionPageView;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Status;
import com.irvinsegura.supportchat.knowledge.TaughtAnswers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Questions the assistant struggled with, so the team can teach their answers.
 */
@RestController
@RequestMapping("/api/v1/difficult-questions")
@Tag(name = "Difficult questions")
@SecurityRequirement(name = "bearerAuth")
class DifficultQuestionController {

    private final DifficultQuestionService questions;

    DifficultQuestionController(DifficultQuestionService questions) {
        this.questions = questions;
    }

    @GetMapping
    @Operation(summary = "Questions whose answers did not help or that the fast model could not answer, most frequent "
            + "first")
    DifficultQuestionPageView list(@RequestParam(defaultValue = "OPEN") Status status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return questions.page(status, page, size);
    }

    @PutMapping("/{questionId}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Dismiss a question or open it again")
    ResponseEntity<Void> changeStatus(@PathVariable UUID questionId,
            @Valid @RequestBody DifficultStatusRequest request) {
        questions.changeStatus(questionId, request.status());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{questionId}/answer")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Teach the answer: it goes into the knowledge base as a document and the question is closed")
    ResponseEntity<TaughtAnswerView> teach(@PathVariable UUID questionId, @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal Jwt operator) {
        UUID documentId = questions.teach(questionId, request.answer(), UUID.fromString(operator.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/knowledge/documents/" + documentId))
                .body(new TaughtAnswerView(documentId));
    }

    @PostMapping("/{questionId}/draft")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Draft the answer from the knowledge base with the precise model, for the team to correct "
            + "before teaching it; counts as a playground answer of the plan")
    DraftView draft(@PathVariable UUID questionId) {
        return new DraftView(questions.draft(questionId).orElse(null));
    }

    /**
     * @param answer null when the documents do not hold the answer
     */
    record DraftView(@Nullable String answer) {
    }

    record DifficultStatusRequest(@NotNull Status status) {
    }

    record AnswerRequest(@NotBlank @Size(max = TaughtAnswers.MAX_ANSWER_CHARS) String answer) {
    }
}

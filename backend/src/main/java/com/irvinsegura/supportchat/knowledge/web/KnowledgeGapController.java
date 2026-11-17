package com.irvinsegura.supportchat.knowledge.web;

import com.irvinsegura.supportchat.knowledge.TaughtAnswers;
import com.irvinsegura.supportchat.knowledge.application.KnowledgeGapService;
import com.irvinsegura.supportchat.knowledge.application.KnowledgeGapService.DocumentSuggestion;
import com.irvinsegura.supportchat.knowledge.application.KnowledgeGapService.GapPageView;
import com.irvinsegura.supportchat.knowledge.domain.model.GapStatus;
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
import java.util.List;
import java.util.UUID;
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
 * Questions the assistant could not answer, so the team knows what to add to the knowledge base next.
 */
@RestController
@RequestMapping("/api/v1/knowledge-gaps")
@Tag(name = "Knowledge gaps")
@SecurityRequirement(name = "bearerAuth")
class KnowledgeGapController {

    private final KnowledgeGapService gaps;

    KnowledgeGapController(KnowledgeGapService gaps) {
        this.gaps = gaps;
    }

    @GetMapping
    @Operation(summary = "Unanswered questions grouped by wording, most asked first")
    GapPageView list(@RequestParam(defaultValue = "OPEN") GapStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return gaps.page(status, page, size);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Documents worth writing: open questions grouped by topic, most asked topics first")
    List<DocumentSuggestion> suggestions() {
        return gaps.suggestions();
    }

    @PutMapping("/{gapId}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Mark a gap as resolved (documented), dismissed (out of scope) or open again")
    ResponseEntity<Void> changeStatus(@PathVariable UUID gapId, @Valid @RequestBody GapStatusRequest request) {
        gaps.changeStatus(gapId, request.status());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{gapId}/answer")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Teach the answer: it goes into the knowledge base as a document and the gap is resolved")
    ResponseEntity<TaughtAnswerView> teach(@PathVariable UUID gapId, @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal Jwt operator) {
        UUID documentId = gaps.teach(gapId, request.answer(), UUID.fromString(operator.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/knowledge/documents/" + documentId))
                .body(new TaughtAnswerView(documentId));
    }

    record GapStatusRequest(@NotNull GapStatus status) {
    }

    record AnswerRequest(@NotBlank @Size(max = TaughtAnswers.MAX_ANSWER_CHARS) String answer) {
    }
}

package com.irvinsegura.supportchat.knowledge.web;

import com.irvinsegura.supportchat.knowledge.KnowledgeMatch;
import com.irvinsegura.supportchat.knowledge.KnowledgeSearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets operators see exactly which passages the assistant would read for a question, to understand an answer or
 * to spot missing knowledge.
 */
@RestController
@Tag(name = "Knowledge base")
@SecurityRequirement(name = "bearerAuth")
class KnowledgeSearchController {

    private final KnowledgeSearch search;

    KnowledgeSearchController(KnowledgeSearch search) {
        this.search = search;
    }

    @PostMapping("/api/v1/knowledge/search")
    @Operation(summary = "Passages retrieved for a question, as the assistant would see them")
    List<KnowledgeMatch> search(@Valid @RequestBody SearchRequest request) {
        return search.search(request.question(), request.limit() == null ? 5 : request.limit());
    }

    record SearchRequest(@NotBlank @Size(max = 2000) String question, @Min(1) @Max(20) Integer limit) {
    }
}

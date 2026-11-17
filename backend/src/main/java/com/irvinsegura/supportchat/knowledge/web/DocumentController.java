package com.irvinsegura.supportchat.knowledge.web;

import com.irvinsegura.supportchat.knowledge.application.DocumentService;
import com.irvinsegura.supportchat.knowledge.application.DocumentService.DocumentPageView;
import com.irvinsegura.supportchat.knowledge.application.DocumentView;
import com.irvinsegura.supportchat.knowledge.domain.model.EmptyDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The workspace's knowledge base. Paths carry document ids, but row-level security makes a document of another
 * workspace indistinguishable from one that does not exist.
 */
@RestController
@RequestMapping("/api/v1/knowledge/documents")
@Tag(name = "Knowledge base")
@SecurityRequirement(name = "bearerAuth")
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Upload a PDF, Word, Excel, Markdown, HTML or text file; searchable once processed")
    ResponseEntity<DocumentView> upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = KnowledgeLimits.MAX_TITLE_LENGTH) String title,
            @AuthenticationPrincipal Jwt operator) throws IOException {
        if (file.isEmpty()) {
            throw new EmptyDocumentException();
        }
        DocumentView created = documents.upload(title, file.getOriginalFilename(), file.getBytes(),
                UUID.fromString(operator.getSubject()));
        // 202: stored, but not searchable until the background ingestion finishes
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/knowledge/documents/" + created.id()))
                .body(created);
    }

    @PostMapping("/web")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Import a web page, and optionally the pages of the same site it links to")
    ResponseEntity<DocumentView> importWebPage(@Valid @RequestBody WebPageRequest request,
            @AuthenticationPrincipal Jwt operator) {
        DocumentView created = documents.importWebPage(request.url(), request.title(), request.linkedPages(),
                UUID.fromString(operator.getSubject()));
        // 202: the page is downloaded and learned in the background
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/knowledge/documents/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Documents, newest first")
    DocumentPageView list(@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return documents.page(page, size);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "One document and its processing status")
    DocumentView get(@PathVariable UUID documentId) {
        return documents.get(documentId);
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Delete a document; the assistant stops using it at once")
    ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        documents.delete(documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{documentId}/reprocess")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Process a document again, e.g. after a failure; a web page is downloaded again")
    ResponseEntity<DocumentView> reprocess(@PathVariable UUID documentId) {
        return ResponseEntity.accepted().body(documents.reprocess(documentId));
    }

    /**
     * @param linkedPages other pages of the same site linked from this one to import too; 0 or left out for none
     */
    record WebPageRequest(@Size(max = KnowledgeLimits.MAX_URL_LENGTH) String url,
            @Size(max = KnowledgeLimits.MAX_TITLE_LENGTH) String title,
            // Boxed: Jackson refuses a missing primitive, and "this page only" is the usual request
            @Min(0) @Max(KnowledgeLimits.MAX_LINKED_PAGES) Integer linkedPages) {

        WebPageRequest {
            linkedPages = linkedPages == null ? 0 : linkedPages;
        }
    }
}

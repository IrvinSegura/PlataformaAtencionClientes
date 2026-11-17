package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.OutcomeReport;
import com.irvinsegura.supportchat.conversations.application.OutcomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * How well the assistant and the team help customers.
 */
@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights")
@SecurityRequirement(name = "bearerAuth")
class OutcomeController {

    private final OutcomeService outcomes;

    OutcomeController(OutcomeService outcomes) {
        this.outcomes = outcomes;
    }

    @GetMapping("/outcomes")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Conversations solved by the assistant alone, handed to the team or unresolved, verdicts on "
            + "answers and ratings, between two UTC days (both included; the last 30 days by default)")
    OutcomeReport outcomes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return outcomes.report(from, to);
    }
}

package com.irvinsegura.supportchat.billing.web;

import com.irvinsegura.supportchat.billing.application.UsageReport;
import com.irvinsegura.supportchat.billing.application.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the operator's workspace consumed, for billing. Costs are only for whoever pays: owners and administrators.
 */
@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage")
@SecurityRequirement(name = "bearerAuth")
class UsageController {

    private final UsageService usage;
    private final Clock clock;

    UsageController(UsageService usage, Clock clock) {
        this.usage = usage;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "One month of usage (default: the current one, in UTC) with its estimated cost")
    UsageReport month(@RequestParam(required = false) YearMonth month) {
        return usage.month(month != null ? month : YearMonth.now(clock.withZone(ZoneOffset.UTC)));
    }
}

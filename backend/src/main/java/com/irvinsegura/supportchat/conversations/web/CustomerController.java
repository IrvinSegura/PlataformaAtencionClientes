package com.irvinsegura.supportchat.conversations.web;

import com.irvinsegura.supportchat.conversations.application.CustomerService;
import com.irvinsegura.supportchat.conversations.application.CustomerService.CustomerPageView;
import com.irvinsegura.supportchat.conversations.application.CustomerService.CustomerView;
import com.irvinsegura.supportchat.conversations.domain.model.CustomerProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customers who write through WhatsApp, email, Messenger or Instagram, recognized across conversations. Any member
 * may read them and keep notes, as they are the ones talking to the customers. A customer is reached through one of
 * their conversations, so their number or address never travels in a URL.
 */
@RestController
@Tag(name = "Customers")
@SecurityRequirement(name = "bearerAuth")
class CustomerController {

    private final CustomerService customers;

    CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping("/api/v1/customers")
    @Operation(summary = "Customers who wrote through a channel, most recently active first; query matches part of "
            + "the number, address or name")
    CustomerPageView list(@RequestParam(required = false) @Size(max = 120) String query,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return customers.page(query, page, size);
    }

    @GetMapping("/api/v1/conversations/{conversationId}/customer")
    @Operation(summary = "The customer of a channel conversation: the team's notes and their other conversations")
    CustomerView get(@PathVariable UUID conversationId) {
        return customers.ofConversation(conversationId);
    }

    @PutMapping("/api/v1/conversations/{conversationId}/customer")
    @Operation(summary = "Change the team's name and notes for the customer, and whether the assistant remembers "
            + "their earlier conversations")
    CustomerView edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody EditRequest request) {
        return customers.edit(conversationId, UUID.fromString(jwt.getSubject()), request.displayName(),
                request.notes(), request.remember());
    }

    /**
     * @param remember null keeps the saved choice
     */
    record EditRequest(@Size(max = CustomerProfile.MAX_DISPLAY_NAME_LENGTH) String displayName,
            @Size(max = CustomerProfile.MAX_NOTES_LENGTH) String notes, Boolean remember) {
    }
}

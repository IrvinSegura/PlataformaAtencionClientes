package com.irvinsegura.supportchat.billing.web;

import com.irvinsegura.supportchat.billing.application.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Stripe reports payments and subscription changes. Public: Stripe's signature over the body proves where a
 * notification comes from.
 */
@RestController
@Tag(name = "Billing")
class StripeWebhookController {

    private final BillingService billing;

    StripeWebhookController(BillingService billing) {
        this.billing = billing;
    }

    /**
     * The body is read as bytes: the signature covers them exactly, before any parsing.
     */
    @PostMapping(path = "/api/v1/billing/stripe/webhook", consumes = MediaType.ALL_VALUE)
    @Operation(summary = "A Stripe notification, signed in the Stripe-Signature header")
    ResponseEntity<Void> receive(@RequestBody byte[] body,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        billing.receive(body, signature);
        return ResponseEntity.ok().build();
    }
}

package com.irvinsegura.supportchat.billing.web;

import com.irvinsegura.supportchat.billing.application.BillingService;
import com.irvinsegura.supportchat.billing.application.BillingViews.BillingOverview;
import com.irvinsegura.supportchat.billing.application.BillingViews.PlanChange;
import com.irvinsegura.supportchat.billing.application.BillingViews.PortalSession;
import com.irvinsegura.supportchat.billing.application.BillingViews.TopUpPurchase;
import com.irvinsegura.supportchat.billing.domain.model.Invoice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's plan and payments. Owners and administrators see them; only the owner, who pays, changes them.
 */
@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing")
@SecurityRequirement(name = "bearerAuth")
class BillingController {

    private final BillingService billing;

    BillingController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "The plan that applies, this month's quotas and the plans on offer")
    BillingOverview overview() {
        return billing.overview();
    }

    @PostMapping("/plan")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Move to a plan: returns Stripe's checkout address for a first purchase, or changes the "
            + "subscription in place")
    PlanChange choosePlan(@Valid @RequestBody PlanRequest request) {
        return billing.choosePlan(request.plan());
    }

    @PostMapping("/top-ups")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Buy a recharge of extra units for this month: returns Stripe's checkout address")
    TopUpPurchase buyTopUp(@Valid @RequestBody TopUpRequest request) {
        return billing.buyTopUp(request.topUp());
    }

    @PostMapping("/portal")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Address of Stripe's customer portal: payment method, invoices, cancellation")
    PortalSession portal() {
        return billing.portal();
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "The latest invoices, newest first")
    List<Invoice> invoices() {
        return billing.invoices();
    }

    record PlanRequest(@NotBlank @Size(max = 32) String plan) {
    }

    record TopUpRequest(@NotBlank @Size(max = 32) String topUp) {
    }
}

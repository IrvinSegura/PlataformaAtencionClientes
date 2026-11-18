package com.irvinsegura.supportchat.billing.domain.port;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.billing.domain.model.Invoice;
import com.irvinsegura.supportchat.billing.domain.model.ProviderEvent;
import com.irvinsegura.supportchat.billing.domain.model.TopUp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Charges workspaces for their plan and for their usage past it. Payment details never reach this platform: the
 * provider's own pages collect them.
 * <p>
 * Every call that reaches the provider throws
 * {@link com.irvinsegura.supportchat.billing.domain.model.PaymentProviderUnavailableException} when it cannot be
 * reached or refuses the request.
 */
public interface PaymentProvider {

    /**
     * Whether keys are set; without them nothing can be bought and every workspace is on the free plan.
     */
    boolean configured();

    /**
     * Whether the provider has the prices of a plan.
     */
    boolean sells(String planCode);

    /**
     * The address of the provider's page where the workspace pays for a plan.
     */
    String checkout(Checkout checkout);

    /**
     * Whether the provider has the one-time price of a recharge.
     */
    boolean sellsTopUp(String topUpCode);

    /**
     * The address of the provider's page where the workspace pays once for a recharge.
     */
    String buyTopUp(TopUpCheckout checkout);

    /**
     * The address of the provider's page where the workspace updates its payment method, sees its invoices or
     * cancels.
     */
    String portal(String customerId, String returnUrl);

    /**
     * Moves a live subscription to another paid plan; the difference is prorated.
     */
    void changePlan(String subscriptionId, String planCode);

    /**
     * Ends a subscription once its paid period is over.
     */
    void cancelAtPeriodEnd(String subscriptionId);

    /**
     * The customer's latest invoices, newest first.
     */
    List<Invoice> invoices(String customerId);

    /**
     * Adds usage to the customer's metered prices. The identifier makes a repeated report count once.
     */
    void reportUsage(String customerId, UsageMetric metric, long quantity, String identifier, Instant at);

    /**
     * Verifies and reads a webhook notification.
     *
     * @throws com.irvinsegura.supportchat.billing.domain.model.InvalidPaymentSignatureException when the signature
     *         does not match or the notification is too old
     */
    ProviderEvent read(byte[] body, String signature, Instant now);

    /**
     * @param customerId        the workspace's customer, or null for a first purchase
     * @param billingCycleStart when the first full period starts, so periods follow calendar months like usage
     */
    record Checkout(UUID workspaceId, String planCode, String customerId, Instant billingCycleStart,
            String successUrl, String cancelUrl) {
    }

    /**
     * @param customerId the workspace's customer, or null when it never paid: the provider creates one
     */
    record TopUpCheckout(UUID workspaceId, TopUp topUp, String customerId, String successUrl, String cancelUrl) {
    }
}

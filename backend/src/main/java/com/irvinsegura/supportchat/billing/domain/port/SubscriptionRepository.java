package com.irvinsegura.supportchat.billing.domain.port;

import com.irvinsegura.supportchat.billing.domain.model.Subscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The subscription of the workspace bound to the current transaction, and the few lookups that notifications from
 * the payment provider need before a workspace is known.
 */
public interface SubscriptionRepository {

    Optional<Subscription> find();

    void save(Subscription subscription);

    /**
     * The workspace of a customer of the payment provider. Works without a workspace bound.
     */
    Optional<UUID> workspaceOfCustomer(String customerId);

    /**
     * Records which workspace a customer pays for. Works without a workspace bound.
     */
    void linkCustomer(UUID workspaceId, String customerId);

    /**
     * Every workspace that has a customer, for the job that reports usage. Works without a workspace bound.
     */
    List<UUID> workspacesWithCustomer();

    /**
     * Records a notification's id; false when it was seen before, since the provider may deliver it again. Works
     * without a workspace bound.
     */
    boolean firstEvent(String eventId, Instant receivedAt);
}

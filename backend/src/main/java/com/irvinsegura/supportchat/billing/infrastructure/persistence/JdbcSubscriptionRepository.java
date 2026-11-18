package com.irvinsegura.supportchat.billing.infrastructure.persistence;

import com.irvinsegura.supportchat.billing.domain.model.Subscription;
import com.irvinsegura.supportchat.billing.domain.model.SubscriptionStatus;
import com.irvinsegura.supportchat.billing.domain.port.SubscriptionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcSubscriptionRepository implements SubscriptionRepository {

    private final JdbcClient jdbc;

    JdbcSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> find() {
        return jdbc.sql("""
                        SELECT workspace_id, plan_code, status, customer_id, subscription_id, current_period_end,
                               cancel_at_period_end, past_due_since, last_event_at, updated_at
                        FROM subscriptions
                        """)
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    @Transactional
    public void save(Subscription subscription) {
        jdbc.sql("""
                        INSERT INTO subscriptions (workspace_id, plan_code, status, customer_id, subscription_id,
                                                   current_period_end, cancel_at_period_end, past_due_since,
                                                   last_event_at, updated_at)
                        VALUES (:workspaceId, :planCode, :status, :customerId, :subscriptionId, :currentPeriodEnd,
                                :cancelAtPeriodEnd, :pastDueSince, :lastEventAt, :updatedAt)
                        ON CONFLICT (workspace_id) DO UPDATE SET
                            plan_code = EXCLUDED.plan_code,
                            status = EXCLUDED.status,
                            customer_id = EXCLUDED.customer_id,
                            subscription_id = EXCLUDED.subscription_id,
                            current_period_end = EXCLUDED.current_period_end,
                            cancel_at_period_end = EXCLUDED.cancel_at_period_end,
                            past_due_since = EXCLUDED.past_due_since,
                            last_event_at = EXCLUDED.last_event_at,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", subscription.workspaceId())
                .param("planCode", subscription.planCode())
                .param("status", subscription.status().name())
                .param("customerId", subscription.customerId())
                .param("subscriptionId", subscription.subscriptionId())
                .param("currentPeriodEnd", timestamp(subscription.currentPeriodEnd()))
                .param("cancelAtPeriodEnd", subscription.cancelAtPeriodEnd())
                .param("pastDueSince", timestamp(subscription.pastDueSince()))
                .param("lastEventAt", timestamp(subscription.lastEventAt()))
                .param("updatedAt", timestamp(subscription.updatedAt()))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> workspaceOfCustomer(String customerId) {
        return jdbc.sql("SELECT workspace_id FROM billing_customers WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional
    public void linkCustomer(UUID workspaceId, String customerId) {
        // A workspace keeps its first customer: a second checkout reuses it, so this only fills a missing link
        jdbc.sql("""
                        INSERT INTO billing_customers (workspace_id, customer_id)
                        VALUES (:workspaceId, :customerId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("workspaceId", workspaceId)
                .param("customerId", customerId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> workspacesWithCustomer() {
        return jdbc.sql("SELECT workspace_id FROM billing_customers ORDER BY workspace_id")
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional
    public boolean firstEvent(String eventId, Instant receivedAt) {
        return jdbc.sql("""
                        INSERT INTO payment_events (event_id, received_at)
                        VALUES (:eventId, :receivedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("receivedAt", Timestamp.from(receivedAt))
                .update() == 1;
    }

    private static Subscription map(ResultSet rs) throws SQLException {
        return Subscription.restore(
                rs.getObject("workspace_id", UUID.class),
                rs.getString("plan_code"),
                SubscriptionStatus.valueOf(rs.getString("status")),
                rs.getString("customer_id"),
                rs.getString("subscription_id"),
                instant(rs.getTimestamp("current_period_end")),
                rs.getBoolean("cancel_at_period_end"),
                instant(rs.getTimestamp("past_due_since")),
                instant(rs.getTimestamp("last_event_at")),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

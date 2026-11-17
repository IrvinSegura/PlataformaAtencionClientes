package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.AttentionAlerts.Delivery;
import com.irvinsegura.supportchat.conversations.AttentionReason;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.domain.model.AttentionSettings;
import com.irvinsegura.supportchat.conversations.domain.port.AttentionRepository;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filtered by row-level security like every workspace table: no query here mentions the workspace.
 */
@Repository
class JdbcAttentionRepository implements AttentionRepository {

    private final JdbcClient jdbc;

    JdbcAttentionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttentionSettings> findSettings() {
        return jdbc.sql("""
                        SELECT hand_off_frustrated, alerts_enabled, alert_phones, alert_on_request,
                               alert_on_frustration, alert_template, alert_template_language, alert_on_difficult,
                               alert_on_usage
                          FROM attention_settings
                        """)
                .query((rs, row) -> new AttentionSettings(
                        rs.getBoolean("hand_off_frustrated"),
                        rs.getBoolean("alerts_enabled"),
                        phones(rs.getArray("alert_phones")),
                        rs.getBoolean("alert_on_request"),
                        rs.getBoolean("alert_on_frustration"),
                        rs.getString("alert_template"),
                        rs.getString("alert_template_language"),
                        rs.getBoolean("alert_on_difficult"),
                        rs.getBoolean("alert_on_usage")))
                .optional();
    }

    @Override
    @Transactional
    public void saveSettings(UUID workspaceId, AttentionSettings settings, Instant now) {
        jdbc.sql("""
                        INSERT INTO attention_settings (workspace_id, hand_off_frustrated, alerts_enabled, alert_phones,
                                                        alert_on_request, alert_on_frustration, alert_template,
                                                        alert_template_language, alert_on_difficult, alert_on_usage,
                                                        updated_at)
                        VALUES (:workspaceId, :handOff, :enabled, :phones, :onRequest, :onFrustration, :template,
                                :templateLanguage, :onDifficult, :onUsage, :now)
                        ON CONFLICT (workspace_id) DO UPDATE
                           SET hand_off_frustrated = EXCLUDED.hand_off_frustrated,
                               alerts_enabled = EXCLUDED.alerts_enabled,
                               alert_phones = EXCLUDED.alert_phones,
                               alert_on_request = EXCLUDED.alert_on_request,
                               alert_on_frustration = EXCLUDED.alert_on_frustration,
                               alert_template = EXCLUDED.alert_template,
                               alert_template_language = EXCLUDED.alert_template_language,
                               alert_on_difficult = EXCLUDED.alert_on_difficult,
                               alert_on_usage = EXCLUDED.alert_on_usage,
                               updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", workspaceId)
                .param("handOff", settings.handOffFrustrated())
                .param("enabled", settings.alertsEnabled())
                // A String[] is sent as a PostgreSQL text[]
                .param("phones", settings.alertPhones().toArray(String[]::new))
                .param("onRequest", settings.alertOnRequest())
                .param("onFrustration", settings.alertOnFrustration())
                .param("template", settings.template())
                .param("templateLanguage", settings.templateLanguage())
                .param("onDifficult", settings.alertOnDifficult())
                .param("onUsage", settings.alertOnUsage())
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    @Transactional
    public boolean insertAlertIfNew(UUID id, UUID workspaceId, UUID conversationId, AttentionReason reason,
            String excerpt, Instant now) {
        return jdbc.sql("""
                        INSERT INTO attention_alerts (id, workspace_id, conversation_id, reason, excerpt, created_at)
                        VALUES (:id, :workspaceId, :conversationId, :reason, :excerpt, :now)
                        ON CONFLICT (conversation_id, reason) DO NOTHING
                        """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("conversationId", conversationId)
                .param("reason", reason.name())
                .param("excerpt", excerpt)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    @Override
    @Transactional
    public boolean saveDelivery(UUID alertId, UUID workspaceId, String recipient, Delivery delivery, String detail,
            Instant now) {
        // Selecting the alert keeps a delivery from naming an alert of another workspace, which RLS hides
        return jdbc.sql("""
                        INSERT INTO attention_alert_deliveries (alert_id, workspace_id, recipient, status, detail,
                                                                delivered_at)
                        SELECT id, :workspaceId, :recipient, :status, :detail, :now
                          FROM attention_alerts
                         WHERE id = :alertId
                        ON CONFLICT (alert_id, recipient) DO UPDATE
                           SET status = EXCLUDED.status, detail = EXCLUDED.detail,
                               delivered_at = EXCLUDED.delivered_at
                        """)
                .param("alertId", alertId)
                .param("workspaceId", workspaceId)
                .param("recipient", recipient)
                .param("status", delivery.name())
                .param("detail", detail)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecordedAlert> recentAlerts(int limit) {
        Map<UUID, AlertRow> alerts = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT a.id, a.conversation_id, c.channel, c.contact_id, c.contact_name, a.reason, a.excerpt,
                               a.created_at, d.recipient, d.status, d.detail, d.delivered_at
                          FROM (SELECT * FROM attention_alerts ORDER BY created_at DESC, id DESC LIMIT :limit) a
                          JOIN conversations c ON c.id = a.conversation_id
                          LEFT JOIN attention_alert_deliveries d ON d.alert_id = a.id
                         ORDER BY a.created_at DESC, a.id DESC, d.recipient
                        """)
                .param("limit", limit)
                .query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    AlertRow alert = alerts.get(id);
                    if (alert == null) {
                        alert = new AlertRow(id, rs.getObject("conversation_id", UUID.class),
                                ConversationChannel.valueOf(rs.getString("channel")), rs.getString("contact_id"),
                                rs.getString("contact_name"), AttentionReason.valueOf(rs.getString("reason")),
                                rs.getString("excerpt"), rs.getTimestamp("created_at").toInstant(),
                                new ArrayList<>());
                        alerts.put(id, alert);
                    }
                    String recipient = rs.getString("recipient");
                    if (recipient != null) {
                        alert.deliveries().add(new RecordedDelivery(recipient,
                                Delivery.valueOf(rs.getString("status")), rs.getString("detail"),
                                rs.getTimestamp("delivered_at").toInstant()));
                    }
                });
        return alerts.values().stream()
                .map(row -> new RecordedAlert(row.id(), row.conversationId(), row.channel(), row.contactId(),
                        row.contactName(), row.reason(), row.excerpt(), row.createdAt(), row.deliveries()))
                .toList();
    }

    private static List<String> phones(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private record AlertRow(UUID id, UUID conversationId, ConversationChannel channel, String contactId,
            String contactName, AttentionReason reason, String excerpt, Instant createdAt,
            List<RecordedDelivery> deliveries) {
    }
}

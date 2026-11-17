package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.AttentionAlerts.Delivery;
import com.irvinsegura.supportchat.conversations.AttentionReason;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.domain.model.AttentionSettings;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The current workspace's attention settings and the alerts it raised, with how each one was delivered.
 */
public interface AttentionRepository {

    Optional<AttentionSettings> findSettings();

    void saveSettings(UUID workspaceId, AttentionSettings settings, Instant now);

    /**
     * Records an alert unless the conversation already raised one for that reason.
     *
     * @return whether it was recorded
     */
    boolean insertAlertIfNew(UUID id, UUID workspaceId, UUID conversationId, AttentionReason reason, String excerpt,
            Instant now);

    /**
     * Records how sending to one recipient went, replacing what an earlier try recorded.
     *
     * @return false when no such alert exists in the workspace
     */
    boolean saveDelivery(UUID alertId, UUID workspaceId, String recipient, Delivery delivery, String detail,
            Instant now);

    List<RecordedAlert> recentAlerts(int limit);

    record RecordedAlert(UUID id, UUID conversationId, ConversationChannel channel, String contactId,
            String contactName, AttentionReason reason, String excerpt, Instant createdAt,
            List<RecordedDelivery> deliveries) {

        public RecordedAlert {
            deliveries = List.copyOf(deliveries);
        }
    }

    record RecordedDelivery(String recipient, Delivery delivery, String detail, Instant deliveredAt) {
    }
}

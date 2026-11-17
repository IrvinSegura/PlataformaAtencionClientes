package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.AttentionAlerts;
import com.irvinsegura.supportchat.conversations.AttentionReason;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.CustomerNeedsAttention;
import com.irvinsegura.supportchat.conversations.OwnerAlertRecipients;
import com.irvinsegura.supportchat.conversations.application.ConversationMetrics.HandoffReason;
import com.irvinsegura.supportchat.conversations.domain.model.AttentionSettings;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.FrustrationDetector;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.SystemNotice;
import com.irvinsegura.supportchat.conversations.domain.port.AttentionRepository;
import com.irvinsegura.supportchat.conversations.domain.port.AttentionRepository.RecordedAlert;
import com.irvinsegura.supportchat.conversations.domain.port.AttentionRepository.RecordedDelivery;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notices customers who need a person, those who ask for one and those who seem upset, and tells the owner.
 * <p>
 * An upset customer is handed to the team when the workspace wants it. Each conversation raises at most one alert
 * per reason, recorded whether or not anybody is alerted, so the panel shows them all; when the workspace gave
 * WhatsApp numbers, a {@link CustomerNeedsAttention} goes out for the channels module to deliver once the
 * conversation is saved.
 */
@Service
public class AttentionService implements AttentionAlerts, OwnerAlertRecipients {

    private static final Logger log = LoggerFactory.getLogger(AttentionService.class);
    static final int MAX_EXCERPT = 280;
    static final int RECENT_ALERTS = 50;

    private final AttentionRepository attention;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final LiveUpdates live;
    private final ConversationMetrics metrics;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AttentionService(AttentionRepository attention, ConversationRepository conversations, MessageRepository messages,
            LiveUpdates live, ConversationMetrics metrics, ApplicationEventPublisher events, Clock clock) {
        this.attention = attention;
        this.conversations = conversations;
        this.messages = messages;
        this.live = live;
        this.metrics = metrics;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttentionSettings settings() {
        return attention.findSettings().orElse(AttentionSettings.DEFAULTS);
    }

    @Transactional
    public AttentionSettings configure(AttentionSettings settings) {
        attention.saveSettings(TenantContext.requireWorkspaceId(), settings, clock.instant());
        log.info("Attention settings saved for workspace {}: hand off upset customers {}, alerts {} to {} numbers",
                TenantContext.requireWorkspaceId(), settings.handOffFrustrated(), settings.alertsEnabled(),
                settings.alertPhones().size());
        return settings;
    }

    @Transactional(readOnly = true)
    public List<AlertView> recentAlerts() {
        return attention.recentAlerts(RECENT_ALERTS).stream().map(AlertView::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Recipients usageLimit() {
        AttentionSettings settings = settings();
        return new Recipients(settings.usageRecipients(), settings.template(), settings.templateLanguage());
    }

    @Override
    @Transactional
    public void recordDelivery(UUID alertId, String recipient, Delivery delivery, String detail) {
        if (!attention.saveDelivery(alertId, TenantContext.requireWorkspaceId(), recipient, delivery, detail,
                clock.instant())) {
            log.warn("Delivery of unknown alert {} ignored", alertId);
        }
    }

    /**
     * Reads a customer's message just saved in a conversation the caller locked; an upset one alerts the team and,
     * if the workspace wants, hands the conversation to it. Must run inside the caller's transaction.
     *
     * @return whether the conversation went to the team
     */
    boolean screen(Conversation conversation, String text, Instant now) {
        if (!FrustrationDetector.isFrustrated(text)) {
            return false;
        }
        if (!conversation.markFrustrated(now)) {
            // Already upset before: the team was told then
            return false;
        }
        conversations.update(conversation);
        live.conversationChanged(conversation);
        AttentionSettings settings = settings();
        raise(conversation, AttentionReason.FRUSTRATED, text, settings, now);
        if (!settings.handOffFrustrated() || !conversation.requestHandoff(now)) {
            return false;
        }
        Message notice = Message.notice(Ids.newId(), conversation.id(), SystemNotice.HANDOFF_REQUESTED, null, null,
                now);
        conversations.update(conversation);
        messages.insert(notice);
        live.messageAdded(conversation, notice);
        live.conversationChanged(conversation);
        metrics.handedOff(conversation.channel(), HandoffReason.FRUSTRATED);
        return true;
    }

    /**
     * The customer asked for a person. Must run inside the caller's transaction.
     *
     * @param text what they wrote, or null when they pressed a button
     */
    void askedForPerson(Conversation conversation, String text, Instant now) {
        raise(conversation, AttentionReason.ASKED_FOR_PERSON, text, settings(), now);
    }

    private void raise(Conversation conversation, AttentionReason reason, String text, AttentionSettings settings,
            Instant now) {
        UUID alertId = Ids.newId();
        String excerpt = text == null || text.isBlank() ? null : shorten(text.strip());
        if (!attention.insertAlertIfNew(alertId, conversation.workspaceId(), conversation.id(), reason, excerpt,
                now)) {
            return;
        }
        List<String> recipients = settings.recipientsFor(reason);
        if (recipients.isEmpty()) {
            return;
        }
        events.publishEvent(new CustomerNeedsAttention(alertId, conversation.workspaceId(), conversation.id(),
                conversation.channel(), conversation.contactId(), conversation.contactName(), reason, excerpt,
                recipients, settings.template(), settings.templateLanguage(), now));
    }

    private static String shorten(String text) {
        return text.length() <= MAX_EXCERPT ? text : text.substring(0, MAX_EXCERPT - 1) + "…";
    }

    /**
     * An alert as the panel lists it.
     *
     * @param deliveries one per number alerted; empty when nobody was
     */
    public record AlertView(UUID id, UUID conversationId, ConversationChannel channel, @Nullable String contactId,
            @Nullable String contactName, AttentionReason reason, @Nullable String excerpt, Instant createdAt,
            List<DeliveryView> deliveries) {

        static AlertView of(RecordedAlert alert) {
            return new AlertView(alert.id(), alert.conversationId(), alert.channel(), alert.contactId(),
                    alert.contactName(), alert.reason(), alert.excerpt(), alert.createdAt(),
                    alert.deliveries().stream().map(DeliveryView::of).toList());
        }
    }

    public record DeliveryView(String recipient, Delivery status, @Nullable String detail, Instant deliveredAt) {

        static DeliveryView of(RecordedDelivery delivery) {
            return new DeliveryView(delivery.recipient(), delivery.delivery(), delivery.detail(),
                    delivery.deliveredAt());
        }
    }
}

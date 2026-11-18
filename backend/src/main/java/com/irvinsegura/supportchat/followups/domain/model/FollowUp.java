package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate.WhatsAppTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A message a member scheduled to write to a customer later, e.g. three days after a delivery. It goes out once, as a
 * reply of that member, unless it is cancelled first.
 * <p>
 * WhatsApp, Messenger and Instagram only deliver a business's message within 24 hours of the customer's last one. After
 * that, WhatsApp still takes an approved template; Messenger and Instagram take nothing, so the follow-up fails and
 * says why.
 */
public final class FollowUp {

    /**
     * How long after the customer's last message the channels take a normal message.
     */
    public static final Duration CUSTOMER_SERVICE_WINDOW = Duration.ofHours(24);

    /**
     * Furthest ahead a follow-up may be scheduled.
     */
    public static final Duration MAX_DELAY = Duration.ofDays(90);

    /**
     * The placeholder that becomes the customer's name.
     */
    public static final String NAME_PLACEHOLDER = "{nombre}";

    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("[ \\t]+([,.;:!?])");

    private final UUID id;
    private final UUID conversationId;
    private final UUID templateId;
    private final String body;
    private final WhatsAppTemplate whatsapp;
    private final Instant dueAt;
    private FollowUpStatus status;
    private String failure;
    private final UUID createdBy;
    private final String createdByName;
    private final Instant createdAt;
    private Instant finishedAt;
    private UUID sentConversationId;
    private UUID messageId;

    private FollowUp(UUID id, UUID conversationId, UUID templateId, String body, WhatsAppTemplate whatsapp,
            Instant dueAt, FollowUpStatus status, String failure, UUID createdBy, String createdByName,
            Instant createdAt, Instant finishedAt, UUID sentConversationId, UUID messageId) {
        this.id = Objects.requireNonNull(id);
        this.conversationId = Objects.requireNonNull(conversationId);
        this.templateId = templateId;
        this.body = Objects.requireNonNull(body);
        this.whatsapp = whatsapp;
        this.dueAt = Objects.requireNonNull(dueAt);
        this.status = Objects.requireNonNull(status);
        this.failure = failure;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdByName = Objects.requireNonNull(createdByName);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.finishedAt = finishedAt;
        this.sentConversationId = sentConversationId;
        this.messageId = messageId;
    }

    /**
     * Schedules a follow-up for the customer of a conversation.
     *
     * @param channel  the channel the conversation is on
     * @param template the template it comes from, or null for a text written for this customer alone
     * @param text     the text, when there is no template
     * @throws InvalidFollowUpException for a web visitor, who cannot be written to later; for a moment in the past
     *                                  or too far ahead; or for a missing or too long text
     */
    public static FollowUp schedule(UUID id, UUID conversationId, ConversationChannel channel,
            FollowUpTemplate template, String text, Instant dueAt, UUID memberId, String memberName, Instant now) {
        if (channel == ConversationChannel.WEB) {
            throw new InvalidFollowUpException(InvalidFollowUpException.Reason.WEB_VISITOR);
        }
        // A minute of slack, so "now" chosen in a form a moment ago still counts
        if (dueAt == null || dueAt.isBefore(now.minus(Duration.ofMinutes(1))) || dueAt.isAfter(now.plus(MAX_DELAY))) {
            throw new InvalidFollowUpException(InvalidFollowUpException.Reason.DUE_AT);
        }
        String body = template != null ? template.body() : text == null ? "" : text.strip();
        if (body.isEmpty() || body.length() > FollowUpTemplate.MAX_BODY_LENGTH) {
            throw new InvalidFollowUpException(InvalidFollowUpException.Reason.TEXT);
        }
        return new FollowUp(id, conversationId, template == null ? null : template.id(), body,
                template == null ? null : template.whatsapp(), dueAt, FollowUpStatus.SCHEDULED, null, memberId,
                memberName, now, null, null, null);
    }

    public static FollowUp restore(UUID id, UUID conversationId, UUID templateId, String body,
            WhatsAppTemplate whatsapp, Instant dueAt, FollowUpStatus status, String failure, UUID createdBy,
            String createdByName, Instant createdAt, Instant finishedAt, UUID sentConversationId, UUID messageId) {
        return new FollowUp(id, conversationId, templateId, body, whatsapp, dueAt, status, failure, createdBy,
                createdByName, createdAt, finishedAt, sentConversationId, messageId);
    }

    /**
     * How the follow-up can reach the customer now, given when they last wrote.
     *
     * @param lastCustomerMessageAt the customer's latest message on the channel, or null if they never wrote
     * @return the WhatsApp template to send instead of the text, when one is needed and set; empty to send the text
     * @throws FollowUpUndeliverableException when the channel takes nothing from the business any more
     */
    public Optional<WhatsAppTemplate> route(ConversationChannel channel, Instant lastCustomerMessageAt,
            Instant now) {
        boolean windowOpen = lastCustomerMessageAt != null
                && lastCustomerMessageAt.plus(CUSTOMER_SERVICE_WINDOW).isAfter(now);
        return switch (channel) {
            case EMAIL -> Optional.empty();
            case WHATSAPP -> {
                if (windowOpen) {
                    yield Optional.empty();
                }
                if (whatsapp == null) {
                    throw new FollowUpUndeliverableException(FollowUpUndeliverableException.WINDOW_CLOSED_NO_TEMPLATE);
                }
                yield Optional.of(whatsapp);
            }
            case MESSENGER, INSTAGRAM -> {
                if (!windowOpen) {
                    throw new FollowUpUndeliverableException(FollowUpUndeliverableException.WINDOW_CLOSED);
                }
                yield Optional.empty();
            }
            case WEB -> throw new FollowUpUndeliverableException(FollowUpUndeliverableException.WEB_VISITOR);
        };
    }

    /**
     * The text the customer reads, with their name where the placeholder was.
     *
     * @param customerName the name the team or the channel knows them by, or null
     */
    public String textFor(String customerName) {
        String name = customerName == null ? "" : customerName.strip();
        String text = body.replace(NAME_PLACEHOLDER, name);
        // "Hola {nombre}, ..." without a name reads "Hola, ..." rather than "Hola , ..."
        return SPACE_BEFORE_PUNCTUATION.matcher(text).replaceAll("$1").replaceAll(" {2,}", " ").strip();
    }

    public boolean isDue(Instant now) {
        return status == FollowUpStatus.SCHEDULED && !dueAt.isAfter(now);
    }

    public void cancel(Instant now) {
        if (status != FollowUpStatus.SCHEDULED) {
            throw new FollowUpNotScheduledException();
        }
        status = FollowUpStatus.CANCELLED;
        finishedAt = now;
    }

    public void sent(UUID conversationId, UUID messageId, Instant now) {
        status = FollowUpStatus.SENT;
        sentConversationId = Objects.requireNonNull(conversationId);
        this.messageId = Objects.requireNonNull(messageId);
        finishedAt = now;
    }

    /**
     * @param reason a stable code saying why, e.g. {@link FollowUpUndeliverableException#WINDOW_CLOSED}
     */
    public void failed(String reason, Instant now) {
        status = FollowUpStatus.FAILED;
        failure = Objects.requireNonNull(reason);
        finishedAt = now;
    }

    /**
     * The channel reported, after it was sent, that the message never reached the customer. It keeps the message it
     * went out as.
     *
     * @param reason a stable code, e.g. {@link FollowUpUndeliverableException#DELIVERY_REFUSED}
     * @return false when it was not sent, so there is nothing to change
     */
    public boolean notDelivered(String reason, Instant now) {
        if (status != FollowUpStatus.SENT) {
            return false;
        }
        failed(reason, now);
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public UUID templateId() {
        return templateId;
    }

    public String body() {
        return body;
    }

    public WhatsAppTemplate whatsapp() {
        return whatsapp;
    }

    public Instant dueAt() {
        return dueAt;
    }

    public FollowUpStatus status() {
        return status;
    }

    public String failure() {
        return failure;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public String createdByName() {
        return createdByName;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public UUID sentConversationId() {
        return sentConversationId;
    }

    public UUID messageId() {
        return messageId;
    }
}

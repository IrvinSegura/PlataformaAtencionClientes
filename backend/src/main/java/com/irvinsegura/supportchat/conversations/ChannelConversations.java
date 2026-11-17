package com.irvinsegura.supportchat.conversations;

import java.util.UUID;

/**
 * Conversations with customers who write from a messaging channel such as WhatsApp, for the workspace bound to the
 * current code. Replies leave as {@link ConversationReply} events. Methods may call the language model and the
 * business's actions, so they must run outside any transaction.
 */
public interface ChannelConversations {

    /**
     * A customer wrote: the message joins their open conversation on that channel, or starts one, and is answered by
     * the assistant or kept for the team, like a widget question.
     */
    void receive(InboundMessage message);

    /**
     * The customer pressed confirm or cancel on an action the assistant proposed.
     *
     * @param confirmed false cancels it
     */
    void respondToAction(ConversationChannel channel, String contactId, UUID runId, boolean confirmed);

    /**
     * @param contactId   the customer's identity as the channel verified it, e.g. their phone number
     * @param contactName the name the customer shows on the channel, or null
     * @param model       the model the business chose for this channel; null lets the platform pick per question
     */
    record InboundMessage(ConversationChannel channel, String contactId, String contactName, String text,
            ModelPreference model) {

        public InboundMessage {
            model = model == null ? ModelPreference.AUTOMATIC : model;
        }

        public InboundMessage(ConversationChannel channel, String contactId, String contactName, String text) {
            this(channel, contactId, contactName, text, ModelPreference.AUTOMATIC);
        }
    }
}

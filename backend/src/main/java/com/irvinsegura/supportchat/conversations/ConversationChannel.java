package com.irvinsegura.supportchat.conversations;

/**
 * Where a customer talks from.
 */
public enum ConversationChannel {
    // The widget embedded in the business's site; anonymous visitors holding a conversation token
    WEB,
    // WhatsApp Business; customers identified by their phone number
    WHATSAPP,
    // The business's mailbox; customers identified by their email address
    EMAIL,
    // The business's Facebook Page; customers identified by their page-scoped id
    MESSENGER,
    // The business's Instagram professional account; customers identified by their Instagram-scoped id
    INSTAGRAM
}

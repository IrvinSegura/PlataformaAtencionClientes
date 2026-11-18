package com.irvinsegura.supportchat.followups.domain.model;

/**
 * The channel takes no message from the business for this customer now. Not an error of the request: the follow-up is
 * recorded as failed with the code.
 */
public class FollowUpUndeliverableException extends RuntimeException {

    /**
     * Messenger or Instagram, 24 hours after the customer's last message.
     */
    public static final String WINDOW_CLOSED = "WINDOW_CLOSED";

    /**
     * WhatsApp, 24 hours after the customer's last message, with no approved template to send instead.
     */
    public static final String WINDOW_CLOSED_NO_TEMPLATE = "WINDOW_CLOSED_NO_TEMPLATE";

    public static final String WEB_VISITOR = "WEB_VISITOR";

    /**
     * The template says only the customer's name, and the name is unknown, so there is nothing to send.
     */
    public static final String NO_TEXT = "NO_TEXT";

    /**
     * The customer's conversation is gone from the workspace.
     */
    public static final String CONVERSATION_GONE = "CONVERSATION_GONE";

    /**
     * The workspace's account for the channel was disconnected or disabled when the message went out.
     */
    public static final String CHANNEL_UNAVAILABLE = "CHANNEL_UNAVAILABLE";

    /**
     * The channel refused the message for good, e.g. a WhatsApp template it does not know or a rejected address.
     */
    public static final String DELIVERY_REFUSED = "DELIVERY_REFUSED";

    /**
     * Sending failed for a reason no other code covers; the server log has the details.
     */
    public static final String SEND_ERROR = "SEND_ERROR";

    private final String code;

    public FollowUpUndeliverableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

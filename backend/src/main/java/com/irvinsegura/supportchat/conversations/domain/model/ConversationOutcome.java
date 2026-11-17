package com.irvinsegura.supportchat.conversations.domain.model;

/**
 * How a conversation ended up, for the outcomes report.
 */
public enum ConversationOutcome {
    /**
     * Only the assistant took part, its last answer answered the question and the customer did not say otherwise:
     * the latest verdict on an answer is not "unhelpful" and the rating, if any, is 3 or more.
     */
    RESOLVED,
    /**
     * A person of the team was asked for or took part, including when the plan paused the assistant.
     */
    HANDED_OFF,
    /**
     * The assistant alone did not solve it: its last answer said it did not know or failed, or the customer said it
     * did not help.
     */
    UNRESOLVED
}

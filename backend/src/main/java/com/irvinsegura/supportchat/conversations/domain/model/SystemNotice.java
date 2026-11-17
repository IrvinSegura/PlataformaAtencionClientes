package com.irvinsegura.supportchat.conversations.domain.model;

/**
 * What a SYSTEM message records. The code is stored as the message content and each client words it in its own
 * language.
 */
public enum SystemNotice {
    HANDOFF_REQUESTED,
    HANDOFF_CANCELLED,
    // The message's author name is the agent who joined or left
    AGENT_JOINED,
    AGENT_LEFT,
    CONVERSATION_CLOSED,
    // The plan's answers for the month are used up; the team answers until next month or a better plan
    ASSISTANT_PAUSED
}

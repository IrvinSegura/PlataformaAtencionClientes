package com.irvinsegura.supportchat.conversations.domain.model;

/**
 * Who answers the visitor's messages.
 */
public enum ConversationHandling {
    // The assistant answers from the knowledge base
    ASSISTANT,
    // The visitor asked for a person and nobody has taken the conversation yet; messages wait for them
    WAITING,
    // A member of the team answers
    AGENT
}

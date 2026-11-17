package com.irvinsegura.supportchat.conversations.domain.model;

public enum MessageAuthor {
    VISITOR,
    ASSISTANT,
    // A member of the team, after taking the conversation over
    AGENT,
    // A notice of what happened, whose content is a SystemNotice code
    SYSTEM
}

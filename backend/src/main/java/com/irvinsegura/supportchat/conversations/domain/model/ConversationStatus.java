package com.irvinsegura.supportchat.conversations.domain.model;

public enum ConversationStatus {
    OPEN,
    // No more questions accepted, e.g. after reaching the per-conversation limit
    CLOSED
}

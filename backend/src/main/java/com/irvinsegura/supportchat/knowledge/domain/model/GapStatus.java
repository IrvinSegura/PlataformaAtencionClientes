package com.irvinsegura.supportchat.knowledge.domain.model;

public enum GapStatus {
    // Visitors asked and the knowledge base had no answer
    OPEN,
    // An operator documented it; asking again reopens it, because the fix did not work
    RESOLVED,
    // Out of scope for the assistant; stays dismissed however often it is asked
    DISMISSED
}

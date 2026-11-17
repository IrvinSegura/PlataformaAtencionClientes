package com.irvinsegura.supportchat.conversations;

/**
 * Which Claude model answers a channel's customers, as the business chose it on the panel. The models behind each
 * choice are configuration (for example Claude Haiku and Claude Sonnet), so moving every number to a newer model
 * needs no change here.
 */
public enum ModelPreference {
    // The platform picks per question: the fast model for direct questions, the precise one for the rest
    AUTOMATIC,
    // Always the fast, economical model
    FAST,
    // Always the most capable model
    PRECISE
}

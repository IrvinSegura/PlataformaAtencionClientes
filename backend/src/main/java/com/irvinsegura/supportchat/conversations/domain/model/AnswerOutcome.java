package com.irvinsegura.supportchat.conversations.domain.model;

public enum AnswerOutcome {
    // Answered from the knowledge base, with citations
    ANSWERED,
    // The knowledge base has no answer; the visitor was told so honestly
    NO_ANSWER,
    // Something failed (model or search unavailable, visitor left mid-answer)
    FAILED,
    // A greeting, thanks or goodbye, answered with a fixed courtesy reply and no model call
    SMALL_TALK
}

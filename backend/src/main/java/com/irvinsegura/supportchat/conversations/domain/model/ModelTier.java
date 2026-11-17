package com.irvinsegura.supportchat.conversations.domain.model;

/**
 * How capable a model a question needs. Each tier maps to one configured model, e.g. Claude Haiku and Claude Sonnet.
 */
public enum ModelTier {
    // Direct questions one source answers: the cheap, fast model
    SIMPLE,
    // Questions that need reasoning, combining sources or several steps: the more capable model
    COMPLEX
}

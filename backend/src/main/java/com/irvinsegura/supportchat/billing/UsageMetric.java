package com.irvinsegura.supportchat.billing;

/**
 * What is measured. The dimension recorded with each metric says what it is split by.
 */
public enum UsageMetric {
    // Questions the assistant answered; dimension: the channel (WEB, WHATSAPP)
    ANSWERS,
    // Language model tokens; dimension: the model that used them, since each model has its own price
    INPUT_TOKENS,
    OUTPUT_TOKENS,
    // Webhooks of the business the assistant called; dimension: the action's name
    ACTION_RUNS,
    // Messages exchanged through WhatsApp; dimension: inbound or outbound
    WHATSAPP_MESSAGES
}

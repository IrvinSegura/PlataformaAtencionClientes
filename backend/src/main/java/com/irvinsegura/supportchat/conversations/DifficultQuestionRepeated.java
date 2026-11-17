package com.irvinsegura.supportchat.conversations;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A question the assistant does not answer well keeps coming back and nobody has taught its answer yet, published once
 * per question when the workspace alerts someone. The channels module tells them by WhatsApp, so the team teaches the
 * answer before more customers ask.
 *
 * @param question         the latest wording customers used, cut short
 * @param occurrences      how many times customers found it difficult
 * @param recipients       WhatsApp numbers to alert, digits only with the country code
 * @param template         the approved WhatsApp template to send instead of a free text, or null
 * @param templateLanguage the template's language code, e.g. {@code es_MX}; null without a template
 */
public record DifficultQuestionRepeated(UUID workspaceId, UUID questionId, String question, long occurrences,
        List<String> recipients, String template, String templateLanguage, Instant occurredAt) {

    public DifficultQuestionRepeated {
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(questionId);
        Objects.requireNonNull(question);
        recipients = List.copyOf(recipients);
    }
}

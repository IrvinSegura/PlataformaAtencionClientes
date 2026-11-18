package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.conversations.CustomerMessages;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A message the team writes to customers again and again ("¿Te llegó tu pedido, {nombre}?"), to schedule it as a
 * follow-up in a couple of clicks.
 *
 * @param body     what the customer reads; {@code {nombre}} becomes their name
 * @param whatsapp the approved WhatsApp template sent instead when the customer has not written for 24 hours, or null
 */
public record FollowUpTemplate(UUID id, String name, String body, @Nullable WhatsAppTemplate whatsapp,
        Instant createdAt, Instant updatedAt) {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_BODY_LENGTH = CustomerMessages.MAX_TEXT_LENGTH;

    public FollowUpTemplate {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(body);
    }

    /**
     * A validated template.
     */
    public static FollowUpTemplate of(UUID id, String name, String body, WhatsAppTemplate whatsapp,
            Instant createdAt, Instant now) {
        String cleanName = name == null ? "" : name.strip();
        String cleanBody = body == null ? "" : body.strip();
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME_LENGTH || cleanBody.isEmpty()
                || cleanBody.length() > MAX_BODY_LENGTH) {
            throw new InvalidFollowUpTemplateException();
        }
        return new FollowUpTemplate(id, cleanName, cleanBody, whatsapp, createdAt, now);
    }

    /**
     * An approved WhatsApp template, the only message WhatsApp delivers after 24 hours without one of the customer.
     *
     * @param name          its name in WhatsApp Manager, e.g. {@code seguimiento_pedido}
     * @param language      its language code, e.g. {@code es_MX}
     * @param nameParameter whether its {{1}} is the customer's name; otherwise it has no variables
     */
    public record WhatsAppTemplate(String name, String language, boolean nameParameter) {

        private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,512}");
        private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(_[A-Z]{2})?");

        public WhatsAppTemplate {
            if (name == null || !NAME.matcher(name).matches() || language == null
                    || !LANGUAGE.matcher(language).matches()) {
                throw new InvalidFollowUpTemplateException();
            }
        }
    }
}

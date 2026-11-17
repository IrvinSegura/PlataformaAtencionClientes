package com.irvinsegura.supportchat.channels.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A message from a template the business had approved in WhatsApp Manager. WhatsApp only lets a business start a
 * conversation, or write after 24 hours of silence, with one of those.
 *
 * @param to         the number, digits only
 * @param name       the template's name
 * @param language   its language code, e.g. {@code es_MX}
 * @param parameters the values of its body variables {{1}}, {{2}}…, in order
 */
public record TemplateMessage(String to, String name, String language, List<String> parameters) {

    public TemplateMessage {
        Objects.requireNonNull(to);
        Objects.requireNonNull(name);
        Objects.requireNonNull(language);
        parameters = List.copyOf(parameters);
    }
}

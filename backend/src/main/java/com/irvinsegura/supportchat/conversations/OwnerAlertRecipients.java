package com.irvinsegura.supportchat.conversations;

import java.util.List;

/**
 * Whom the workspace bound to the current code wants told by WhatsApp about its account, as set in its attention
 * settings, for alerts other modules raise.
 */
public interface OwnerAlertRecipients {

    /**
     * Whom to tell that the month's usage nears what the plan includes; no phones when nobody.
     */
    Recipients usageLimit();

    /**
     * @param phones           WhatsApp numbers, digits only with the country code
     * @param template         the approved WhatsApp template to send instead of a free text, or null
     * @param templateLanguage the template's language code, e.g. {@code es_MX}; null without a template
     */
    record Recipients(List<String> phones, String template, String templateLanguage) {

        public Recipients {
            phones = List.copyOf(phones);
        }
    }
}

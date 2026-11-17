package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.conversations.AttentionReason;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a workspace does when a customer needs a person: whether an upset customer is handed to the team, and whom
 * to alert by WhatsApp. A workspace that never saved its settings gets {@link #DEFAULTS}: upset customers go to the
 * team, and nobody is alerted until a number is given.
 *
 * @param handOffFrustrated an upset customer stops talking to the assistant and waits for the team
 * @param alertPhones       WhatsApp numbers to alert, digits only with the country code; at most three
 * @param template          an approved WhatsApp template; without one, alerts only reach numbers that wrote to the
 *                          business in the last 24 hours, as WhatsApp requires
 * @param templateLanguage  the template's language code, e.g. {@code es_MX}
 * @param alertOnDifficult  alert when a question the assistant does not answer well keeps coming back
 * @param alertOnUsage      alert when the month's usage nears what the plan includes
 */
public record AttentionSettings(boolean handOffFrustrated, boolean alertsEnabled, List<String> alertPhones,
        boolean alertOnRequest, boolean alertOnFrustration, @Nullable String template,
        @Nullable String templateLanguage, boolean alertOnDifficult, boolean alertOnUsage) {

    public static final int MAX_PHONES = 3;
    public static final AttentionSettings DEFAULTS = new AttentionSettings(true, false, List.of(), true, true, null,
            null, true, true);

    // E.164 without the plus: country code and number, 8 to 15 digits
    private static final Pattern PHONE = Pattern.compile("\\d{8,15}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().+-]");
    // Meta's rules for template names and language codes
    private static final Pattern TEMPLATE = Pattern.compile("[a-z0-9_]{1,512}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(_[A-Z]{2})?");

    public AttentionSettings {
        Set<String> phones = new LinkedHashSet<>();
        for (String phone : alertPhones == null ? List.<String>of() : alertPhones) {
            if (phone == null || phone.isBlank()) {
                continue;
            }
            String digits = SEPARATORS.matcher(phone.strip()).replaceAll("");
            if (!PHONE.matcher(digits).matches()) {
                throw new InvalidAttentionSettingsException("A WhatsApp number to alert has 8 to 15 digits with "
                        + "its country code");
            }
            phones.add(digits);
        }
        if (phones.size() > MAX_PHONES) {
            throw new InvalidAttentionSettingsException("At most " + MAX_PHONES + " numbers can be alerted");
        }
        alertPhones = List.copyOf(phones);
        if (alertsEnabled && alertPhones.isEmpty()) {
            throw new InvalidAttentionSettingsException("Give at least one WhatsApp number to alert");
        }
        template = template == null || template.isBlank() ? null : template.strip();
        if (template != null && !TEMPLATE.matcher(template).matches()) {
            throw new InvalidAttentionSettingsException("A template name has lower-case letters, digits and "
                    + "underscores");
        }
        templateLanguage = template == null || templateLanguage == null || templateLanguage.isBlank() ? null
                : templateLanguage.strip();
        if (template != null && (templateLanguage == null || !LANGUAGE.matcher(templateLanguage).matches())) {
            throw new InvalidAttentionSettingsException("A template needs its language code, such as es_MX");
        }
    }

    /**
     * Settings that alert on difficult questions and on usage whenever alerts are on.
     */
    public AttentionSettings(boolean handOffFrustrated, boolean alertsEnabled, List<String> alertPhones,
            boolean alertOnRequest, boolean alertOnFrustration, String template, String templateLanguage) {
        this(handOffFrustrated, alertsEnabled, alertPhones, alertOnRequest, alertOnFrustration, template,
                templateLanguage, true, true);
    }

    /**
     * The numbers to alert for this reason; none when alerts are off or not wanted for it.
     */
    public List<String> recipientsFor(AttentionReason reason) {
        boolean wanted = switch (reason) {
            case ASKED_FOR_PERSON -> alertOnRequest;
            case FRUSTRATED -> alertOnFrustration;
        };
        return alertsEnabled && wanted ? alertPhones : List.of();
    }

    /**
     * The numbers to alert when a difficult question keeps coming back; none when alerts are off or not wanted for it.
     */
    public List<String> difficultQuestionRecipients() {
        return alertsEnabled && alertOnDifficult ? alertPhones : List.of();
    }

    /**
     * The numbers to alert when the plan nears its limit; none when alerts are off or not wanted for it.
     */
    public List<String> usageRecipients() {
        return alertsEnabled && alertOnUsage ? alertPhones : List.of();
    }
}

package com.irvinsegura.supportchat.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who runs the platform and on what terms, as its public legal pages say.
 *
 * @param operatorName the person or company that runs the platform
 * @param contactEmail where people ask about their data or ask for it to be deleted; blank until configured
 * @param updated      the date the privacy policy last changed, as the page shows it
 * @param termsVersion identifies the terms of service in force; changing it asks every operator to accept them again
 * @param termsUpdated the date the terms last changed, as the page shows it
 * @param jurisdiction the law that governs the terms and the courts that settle disputes, as a sentence ending
 */
@ConfigurationProperties("supportchat.legal")
public record LegalProperties(String operatorName, String contactEmail, String updated, String termsVersion,
        String termsUpdated, String jurisdiction) {

    public LegalProperties {
        operatorName = orElse(operatorName, "Plataforma de Atención al Cliente");
        contactEmail = contactEmail == null ? "" : contactEmail.strip();
        updated = orElse(updated, "10 de noviembre de 2026");
        termsVersion = orElse(termsVersion, "2026-11-11");
        termsUpdated = orElse(termsUpdated, "11 de noviembre de 2026");
        jurisdiction = orElse(jurisdiction, "las leyes de los Estados Unidos Mexicanos, y cualquier controversia se "
                + "resolverá ante los tribunales competentes de la Ciudad de México");
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}

package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.conversations.AttentionReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttentionSettingsTest {

    @Test
    void numbersAreKeptAsDigitsWithoutRepeats() {
        AttentionSettings settings = new AttentionSettings(true, true,
                List.of("+52 (55) 1234-5678", "525512345678", " ", "+1 415 555 0100"), true, true, null, null);

        assertThat(settings.alertPhones()).containsExactly("525512345678", "14155550100");
    }

    @Test
    void wrongNumbersAndTooManyAreRefused() {
        assertThatThrownBy(() -> new AttentionSettings(true, true, List.of("12345"), true, true, null, null))
                .isInstanceOf(InvalidAttentionSettingsException.class);
        assertThatThrownBy(() -> new AttentionSettings(true, true, List.of("55-ABC-1234"), true, true, null, null))
                .isInstanceOf(InvalidAttentionSettingsException.class);
        assertThatThrownBy(() -> new AttentionSettings(true, true,
                List.of("5215550000001", "5215550000002", "5215550000003", "5215550000004"), true, true, null, null))
                .isInstanceOf(InvalidAttentionSettingsException.class);
    }

    @Test
    void alertsNeedANumber() {
        assertThatThrownBy(() -> new AttentionSettings(true, true, List.of(), true, true, null, null))
                .isInstanceOf(InvalidAttentionSettingsException.class);
        assertThat(new AttentionSettings(true, false, List.of(), true, true, null, null).alertPhones()).isEmpty();
    }

    @Test
    void aTemplateNeedsAValidNameAndLanguage() {
        assertThat(new AttentionSettings(true, true, List.of("5215550000001"), true, true, " alerta_cliente ",
                "es_MX").template()).isEqualTo("alerta_cliente");
        assertThatThrownBy(() -> new AttentionSettings(true, true, List.of("5215550000001"), true, true,
                "Alerta Cliente", "es_MX")).isInstanceOf(InvalidAttentionSettingsException.class);
        assertThatThrownBy(() -> new AttentionSettings(true, true, List.of("5215550000001"), true, true,
                "alerta_cliente", null)).isInstanceOf(InvalidAttentionSettingsException.class);
        // Without a template its language means nothing
        assertThat(new AttentionSettings(true, false, List.of(), true, true, "", "es_MX").templateLanguage())
                .isNull();
    }

    @Test
    void recipientsDependOnTheReason() {
        AttentionSettings onlyRequests = new AttentionSettings(true, true, List.of("5215550000001"), true, false,
                null, null);

        assertThat(onlyRequests.recipientsFor(AttentionReason.ASKED_FOR_PERSON)).containsExactly("5215550000001");
        assertThat(onlyRequests.recipientsFor(AttentionReason.FRUSTRATED)).isEmpty();
        assertThat(AttentionSettings.DEFAULTS.recipientsFor(AttentionReason.ASKED_FOR_PERSON)).isEmpty();
    }
}

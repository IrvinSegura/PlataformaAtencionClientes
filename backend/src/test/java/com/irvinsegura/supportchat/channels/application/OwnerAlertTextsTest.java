package com.irvinsegura.supportchat.channels.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.conversations.AttentionReason;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.i18n.LocalizedTexts;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The alerts word each metric and reason from texts/owner-alerts, so a new value needs its texts there.
 */
class OwnerAlertTextsTest {

    @ParameterizedTest
    @EnumSource(UsageMetric.class)
    void everyMetricHasANameAndSaysWhatStops(UsageMetric metric) {
        assertThat(OwnerAlerts.usageName(metric)).isNotBlank();
        assertThat(LocalizedTexts.load("owner-alerts").text(Language.ES, "usage.stops." + metric.name())).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(AttentionReason.class)
    void everyReasonSaysWhatHappened(AttentionReason reason) {
        assertThat(OwnerAlerts.what(reason)).isNotBlank();
    }
}

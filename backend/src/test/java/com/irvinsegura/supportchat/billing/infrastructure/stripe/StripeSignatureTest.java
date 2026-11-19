package com.irvinsegura.supportchat.billing.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StripeSignatureTest {

    private static final String SECRET = "whsec_abc";
    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final byte[] BODY = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsABodySignedWithTheSecretRecently() {
        String header = StripeSignature.sign(SECRET, NOW.getEpochSecond() - 60, BODY);

        assertThat(StripeSignature.matches(SECRET, BODY, header, NOW, TOLERANCE)).isTrue();
    }

    @Test
    void acceptsAnyOfSeveralSignaturesWhileASecretIsRolled() {
        long t = NOW.getEpochSecond();
        String valid = StripeSignature.sign(SECRET, t, BODY).split(",")[1];
        String header = "t=" + t + ",v1=" + "00".repeat(32) + "," + valid + ",v0=ignored";

        assertThat(StripeSignature.matches(SECRET, BODY, header, NOW, TOLERANCE)).isTrue();
    }

    @Test
    void refusesAnotherSecretAChangedBodyOrAnOldDelivery() {
        long t = NOW.getEpochSecond();

        assertThat(StripeSignature.matches(SECRET, BODY, StripeSignature.sign("whsec_other", t, BODY), NOW,
                TOLERANCE)).isFalse();
        assertThat(StripeSignature.matches(SECRET, "{}".getBytes(StandardCharsets.UTF_8),
                StripeSignature.sign(SECRET, t, BODY), NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.matches(SECRET, BODY, StripeSignature.sign(SECRET, t - 301, BODY), NOW,
                TOLERANCE)).isFalse();
    }

    @Test
    void refusesMalformedHeadersAndAMissingSecret() {
        assertThat(StripeSignature.matches(SECRET, BODY, null, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.matches(SECRET, BODY, "v1=abc", NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.matches(SECRET, BODY, "t=soon,v1=abc", NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.matches(SECRET, BODY, "t=" + NOW.getEpochSecond() + ",v1=zz", NOW, TOLERANCE))
                .isFalse();
        assertThat(StripeSignature.matches("", BODY, StripeSignature.sign(SECRET, NOW.getEpochSecond(), BODY), NOW,
                TOLERANCE)).isFalse();
    }
}

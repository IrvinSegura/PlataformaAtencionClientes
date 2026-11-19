package com.irvinsegura.supportchat.channels.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel.Settings;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhatsAppChannelTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    @Test
    void connectingNeedsBothSecrets() {
        assertThatThrownBy(() -> WhatsAppChannel.connect(UUID.randomUUID(), "key", "verify",
                new Settings("1234567890", null, "token", " ", true), NOW))
                .isInstanceOf(InvalidWhatsAppChannelException.class);
    }

    @Test
    void blankSecretsKeepTheSavedOnes() {
        WhatsAppChannel channel = WhatsAppChannel.connect(UUID.randomUUID(), "key", "verify",
                new Settings("1234567890", "+52 55 1234 5678", "token", "secret", true), NOW);

        channel.change(new Settings("1234567891", null, "", null, false), NOW.plusSeconds(60));

        assertThat(channel.accessToken()).isEqualTo("token");
        assertThat(channel.appSecret()).isEqualTo("secret");
        assertThat(channel.phoneNumberId()).isEqualTo("1234567891");
        assertThat(channel.enabled()).isFalse();
    }

    @Test
    void thePhoneNumberIdIsDigitsOnly() {
        assertThatThrownBy(() -> WhatsAppChannel.connect(UUID.randomUUID(), "key", "verify",
                new Settings("+52 55", null, "token", "secret", true), NOW))
                .isInstanceOf(InvalidWhatsAppChannelException.class);
    }

    @Test
    void signaturesCoverTheExactBody() {
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String header = WebhookSignature.sign("app-secret", body);

        assertThat(WebhookSignature.matches("app-secret", body, header)).isTrue();
        assertThat(WebhookSignature.matches("app-secret", "{\"entry\": []}".getBytes(StandardCharsets.UTF_8),
                header)).isFalse();
        assertThat(WebhookSignature.matches("other-secret", body, header)).isFalse();
        assertThat(WebhookSignature.matches("app-secret", body, null)).isFalse();
        assertThat(WebhookSignature.matches("app-secret", body, "sha256=not-hex")).isFalse();
    }
}

package com.irvinsegura.supportchat.channels.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.channels.domain.model.EmailChannel.Server;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailAndMetaChannelTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    void aMailboxIsConnectedWithItsServersAndPassword() {
        EmailChannel channel = EmailChannel.connect(UUID.randomUUID(), email(" Soporte@Tienda.MX ", "", "pass"), NOW);

        assertThat(channel.address()).isEqualTo("soporte@tienda.mx");
        assertThat(channel.username()).isEqualTo("soporte@tienda.mx");
        assertThat(channel.imap()).isEqualTo(new Server("imap.gmail.com", 993));
        assertThat(channel.smtp()).isEqualTo(new Server("smtp.gmail.com", 465));
        assertThat(channel.domain()).isEqualTo("tienda.mx");
        assertThat(channel.isOwnAddress("SOPORTE@tienda.mx")).isTrue();
        assertThat(channel.cursor()).isEmpty();
    }

    @Test
    void connectingNeedsAPasswordAndValidSettings() {
        UUID workspace = UUID.randomUUID();

        assertThatThrownBy(() -> EmailChannel.connect(workspace, email("soporte@tienda.mx", null, " "), NOW))
                .isInstanceOf(InvalidEmailChannelException.class);
        assertThatThrownBy(() -> EmailChannel.connect(workspace, email("soporte", null, "pass"), NOW))
                .isInstanceOf(InvalidEmailChannelException.class);
        assertThatThrownBy(() -> EmailChannel.connect(workspace, new EmailChannel.Settings("soporte@tienda.mx", null,
                "imap.gmail.com/x", 993, "smtp.gmail.com", 465, null, "pass", true), NOW))
                .isInstanceOf(InvalidEmailChannelException.class);
        assertThatThrownBy(() -> EmailChannel.connect(workspace, new EmailChannel.Settings("soporte@tienda.mx", null,
                "imap.gmail.com", 993, "smtp.gmail.com", 70_000, null, "pass", true), NOW))
                .isInstanceOf(InvalidEmailChannelException.class);
    }

    @Test
    void aBlankPasswordKeepsTheSavedOneAndAnotherMailboxIsReadAfresh() {
        EmailChannel channel = EmailChannel.connect(UUID.randomUUID(), email("soporte@tienda.mx", null, "pass"), NOW);
        channel.readUpTo(new MailboxCursor(7, 42), NOW);
        channel.failed("  ", NOW);
        assertThat(channel.lastError()).isEqualTo("Unknown error");

        channel.change(email("soporte@tienda.mx", null, ""), NOW);
        assertThat(channel.password()).isEqualTo("pass");
        assertThat(channel.cursor()).contains(new MailboxCursor(7, 42));
        assertThat(channel.lastError()).isEqualTo("Unknown error");

        channel.change(email("soporte@tienda.mx", "ventas@tienda.mx", "new pass"), NOW);
        assertThat(channel.password()).isEqualTo("new pass");
        assertThat(channel.cursor()).isEmpty();
        assertThat(channel.lastError()).isNull();
        assertThat(email("a@b.co", null, "new pass").toString()).doesNotContain("new pass");
    }

    @Test
    void aMetaChannelNeedsBothSecretsAndANumericAccount() {
        UUID workspace = UUID.randomUUID();

        assertThatThrownBy(() -> MetaChannel.connect(workspace, MetaPlatform.MESSENGER, "key", "verify",
                new MetaChannel.Settings("1000001", null, "token", "", true), NOW))
                .isInstanceOf(InvalidMetaChannelException.class);
        assertThatThrownBy(() -> MetaChannel.connect(workspace, MetaPlatform.INSTAGRAM, "key", "verify",
                new MetaChannel.Settings("@tienda", null, "token", "secret", true), NOW))
                .isInstanceOf(InvalidMetaChannelException.class)
                .hasMessageContaining("Instagram");
    }

    @Test
    void blankMetaSecretsKeepTheSavedOnes() {
        MetaChannel channel = MetaChannel.connect(UUID.randomUUID(), MetaPlatform.MESSENGER, "key", "verify",
                new MetaChannel.Settings("1000001", "Tienda", " token ", "secret", true), NOW);

        channel.change(new MetaChannel.Settings(" 1000002 ", null, null, " ", false), NOW.plusSeconds(1));

        assertThat(channel.accessToken()).isEqualTo("token");
        assertThat(channel.appSecret()).isEqualTo("secret");
        assertThat(channel.receivesFor("1000002")).isTrue();
        assertThat(channel.receivesFor("1000001")).isFalse();
        assertThat(channel.enabled()).isFalse();
        assertThat(MetaPlatform.ofPathName("instagram")).contains(MetaPlatform.INSTAGRAM);
        assertThat(MetaPlatform.ofPathName("whatsapp")).isEmpty();
    }

    private static EmailChannel.Settings email(String address, String username, String password) {
        return new EmailChannel.Settings(address, "Soporte", "IMAP.gmail.com", 993, "smtp.gmail.com", 465, username,
                password, true);
    }
}

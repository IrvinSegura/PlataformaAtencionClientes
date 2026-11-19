package com.irvinsegura.supportchat.channels.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.channels.domain.model.InboundEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MailMessagesTest {

    private static final String HEADERS = """
            From: =?UTF-8?Q?Luc=C3=ADa_P=C3=A9rez?= <Lucia@Example.com>
            To: soporte@tienda.mx
            Subject: Re: Mi pedido
            Message-ID: <customer.2@example.com>
            In-Reply-To: <reply.1@tienda.mx>
            References: <customer.1@example.com>
             <reply.1@tienda.mx>
            MIME-Version: 1.0
            """;

    @Test
    void theTextVersionOfAnAlternativeMessageIsRead() throws MessagingException {
        InboundEmail email = read(HEADERS + """
                Content-Type: multipart/alternative; boundary="b1"

                --b1
                Content-Type: text/plain; charset=UTF-8
                Content-Transfer-Encoding: quoted-printable

                =C2=BFYa sali=C3=B3?

                On Tue, 10 Nov 2026 at 9:00, Soporte <soporte@tienda.mx> wrote:
                > Llega en 3 d=C3=ADas.
                --b1
                Content-Type: text/html; charset=UTF-8

                <p>Otra cosa</p>
                --b1--
                """);

        assertThat(email).isEqualTo(new InboundEmail("<customer.2@example.com>", "lucia@example.com", "Lucía Pérez",
                "Re: Mi pedido", "¿Ya salió?", "<reply.1@tienda.mx>",
                List.of("<customer.1@example.com>", "<reply.1@tienda.mx>"), false, false));
    }

    @Test
    void anHtmlOnlyMessageIsReadAsText() throws MessagingException {
        InboundEmail email = read(HEADERS + """
                Content-Type: text/html; charset=UTF-8

                <div>Hola &amp; gracias</div><blockquote>antes</blockquote>
                """);

        assertThat(email.text()).isEqualTo("Hola & gracias");
    }

    @Test
    void programsMarkTheirMail() throws MessagingException {
        String body = "Content-Type: text/plain\n\nFuera de la oficina\n";

        assertThat(read("Auto-Submitted: auto-replied\n" + HEADERS + body).automated()).isTrue();
        assertThat(read("Auto-Submitted: no\n" + HEADERS + body).automated()).isFalse();
        assertThat(read("List-Id: <ofertas.tienda.mx>\n" + HEADERS + body).automated()).isTrue();
        assertThat(read("Precedence: bulk\n" + HEADERS + body).automated()).isTrue();
        assertThat(read(HEADERS.replace("Lucia@Example.com", "MAILER-DAEMON@example.com") + body).automated())
                .isTrue();
        assertThat(read(HEADERS.replace("Lucia@Example.com", "no-reply@example.com") + body).automated()).isTrue();
        assertThat(read(HEADERS + body).automated()).isFalse();
    }

    @Test
    void aFailedDmarcCheckMarksTheSenderAsSpoofed() throws MessagingException {
        String body = "Content-Type: text/plain\n\nHola\n";

        assertThat(read("Authentication-Results: mx.tienda.mx; spf=pass; dmarc=fail (p=REJECT) header.from=example.com\n"
                + HEADERS + body).spoofed()).isTrue();
        assertThat(read("Authentication-Results: mx.tienda.mx; dmarc=pass header.from=example.com\n" + HEADERS + body)
                .spoofed()).isFalse();
    }

    @Test
    void aMessageWithoutIdUsesTheFallbackAndOneWithoutSenderIsSkipped() throws MessagingException {
        String headers = HEADERS.replace("Message-ID: <customer.2@example.com>\n", "");

        assertThat(read(headers + "Content-Type: text/plain\n\nHola\n").messageId()).isEqualTo("<uid.7@imap>");
        assertThat(MailMessages.read(message("To: soporte@tienda.mx\nSubject: x\n\nHola\n"), "<uid.8@imap>"))
                .isEmpty();
    }

    private static InboundEmail read(String raw) throws MessagingException {
        return MailMessages.read(message(raw), "<uid.7@imap>").orElseThrow();
    }

    private static MimeMessage message(String raw) throws MessagingException {
        byte[] bytes = raw.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
    }
}

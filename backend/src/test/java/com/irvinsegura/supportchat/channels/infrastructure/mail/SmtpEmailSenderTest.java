package com.irvinsegura.supportchat.channels.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.OutgoingEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SmtpEmailSenderTest {

    private static final Session SESSION = Session.getInstance(new Properties());
    private static final EmailChannel CHANNEL = EmailChannel.connect(UUID.randomUUID(),
            new EmailChannel.Settings("soporte@tienda.mx", "Soporte Tienda", "imap.gmail.com", 993, "smtp.gmail.com",
                    465, null, "pass", true), Instant.now());

    @Test
    void aReplyKeepsItsOwnIdAndLandsInTheThread() throws MessagingException, IOException {
        MimeMessage message = SmtpEmailSender.message(SESSION, CHANNEL, new OutgoingEmail("ana@example.com",
                "Re: Mi pedido", "Llega en 3 días.", "<reply.1@tienda.mx>", "<customer.2@example.com>",
                List.of("<customer.1@example.com>", "<customer.2@example.com>"), true));

        assertThat(message.getMessageID()).isEqualTo("<reply.1@tienda.mx>");
        assertThat(message.getHeader("In-Reply-To", null)).isEqualTo("<customer.2@example.com>");
        assertThat(message.getHeader("References", null).replaceAll("\\s+", " "))
                .isEqualTo("<customer.1@example.com> <customer.2@example.com>");
        assertThat(message.getHeader("Auto-Submitted", null)).isEqualTo("auto-replied");
        assertThat(message.getSubject()).isEqualTo("Re: Mi pedido");
        assertThat(message.getContent()).isEqualTo("Llega en 3 días.");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Soporte Tienda");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("ana@example.com");
    }

    @Test
    void aPersonsReplyIsNotMarkedAutomatic() throws MessagingException {
        MimeMessage message = SmtpEmailSender.message(SESSION, CHANNEL, new OutgoingEmail("ana@example.com",
                "Re: Tu consulta", "Ya lo reviso.", "<reply.2@tienda.mx>", null, List.of(), false));

        assertThat(message.getHeader("Auto-Submitted")).isNull();
        assertThat(message.getHeader("In-Reply-To")).isNull();
        assertThat(message.getHeader("References")).isNull();
        assertThat(message.getMessageID()).isEqualTo("<reply.2@tienda.mx>");
    }
}

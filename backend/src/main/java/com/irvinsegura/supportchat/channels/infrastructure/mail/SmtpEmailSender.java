package com.irvinsegura.supportchat.channels.infrastructure.mail;

import com.irvinsegura.supportchat.channels.domain.model.DeliveryFailedException;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.InvalidEmailChannelException;
import com.irvinsegura.supportchat.channels.domain.model.OutgoingEmail;
import com.irvinsegura.supportchat.channels.domain.port.EmailSender;
import com.irvinsegura.supportchat.channels.domain.port.MailServerPolicy;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Sends plain-text mail over SMTP, authenticated as the mailbox.
 */
public class SmtpEmailSender implements EmailSender {

    private final MailServerPolicy servers;
    private final MailConnections connections;

    public SmtpEmailSender(MailServerPolicy servers, MailConnections connections) {
        this.servers = servers;
        this.connections = connections;
    }

    @Override
    public void send(EmailChannel channel, OutgoingEmail email) {
        try {
            servers.check(channel.smtp().host());
        } catch (InvalidEmailChannelException e) {
            throw new DeliveryFailedException.Permanent(e.getMessage());
        }
        int port = channel.smtp().port();
        Session session = connections.smtpSession(port);
        try (Transport transport = session.getTransport(connections.smtpProtocol(port))) {
            MimeMessage message = message(session, channel, email);
            transport.connect(channel.smtp().host(), port, channel.username(), channel.password());
            transport.sendMessage(message, message.getAllRecipients());
        } catch (AuthenticationFailedException e) {
            throw new DeliveryFailedException.Permanent("The SMTP server refused the username or password");
        } catch (SendFailedException e) {
            throw new DeliveryFailedException.Permanent("The SMTP server refused the recipient: "
                    + ImapEmailInbox.reason(e));
        } catch (MessagingException e) {
            throw new DeliveryFailedException.Temporary("The SMTP server could not be reached: "
                    + ImapEmailInbox.reason(e), e);
        }
    }

    static MimeMessage message(Session session, EmailChannel channel, OutgoingEmail email)
            throws MessagingException {
        MimeMessage message = new MimeMessage(session) {
            @Override
            protected void updateMessageID() throws MessagingException {
                // The id is chosen beforehand: a proposal's id carries its action for the customer's reply
                setHeader("Message-ID", email.messageId());
            }
        };
        try {
            message.setFrom(new InternetAddress(channel.address(), channel.displayName(),
                    StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(email.to(), true));
        message.setSubject(email.subject(), StandardCharsets.UTF_8.name());
        message.setText(email.text(), StandardCharsets.UTF_8.name());
        if (email.inReplyTo() != null) {
            message.setHeader("In-Reply-To", email.inReplyTo());
        }
        if (!email.references().isEmpty()) {
            message.setHeader("References", MimeUtility.fold("References: ".length(),
                    String.join(" ", email.references())));
        }
        if (email.automatic()) {
            message.setHeader("Auto-Submitted", "auto-replied");
        }
        message.setSentDate(new Date());
        message.saveChanges();
        return message;
    }
}

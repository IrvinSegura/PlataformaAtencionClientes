package com.irvinsegura.supportchat.channels.infrastructure.mail;

import jakarta.mail.Session;
import java.time.Duration;
import java.util.Properties;

/**
 * How the platform connects to mail servers. IMAP always uses TLS (port 993 at most providers); SMTP uses implicit
 * TLS on port 465 and requires STARTTLS on any other port (587). Certificates and host names are checked.
 * <p>
 * Tests turn TLS off to talk to a local mail server.
 *
 * @param tls false only for tests and local development
 */
public record MailConnections(boolean tls, Duration connectTimeout, Duration readTimeout) {

    static final int SMTPS_PORT = 465;

    String imapProtocol() {
        return tls ? "imaps" : "imap";
    }

    String smtpProtocol(int port) {
        return tls && port == SMTPS_PORT ? "smtps" : "smtp";
    }

    Session imapSession() {
        Properties properties = timeouts(imapProtocol());
        return Session.getInstance(properties);
    }

    Session smtpSession(int port) {
        String protocol = smtpProtocol(port);
        Properties properties = timeouts(protocol);
        properties.put("mail." + protocol + ".auth", "true");
        if (tls && port != SMTPS_PORT) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        return Session.getInstance(properties);
    }

    private Properties timeouts(String protocol) {
        Properties properties = new Properties();
        properties.put("mail." + protocol + ".connectiontimeout", String.valueOf(connectTimeout.toMillis()));
        properties.put("mail." + protocol + ".timeout", String.valueOf(readTimeout.toMillis()));
        if (tls) {
            properties.put("mail." + protocol + ".ssl.checkserveridentity", "true");
        }
        return properties;
    }
}

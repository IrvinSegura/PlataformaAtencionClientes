package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.DeliveryFailedException;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.OutgoingEmail;

/**
 * Sends mail from the mailbox over SMTP.
 */
public interface EmailSender {

    /**
     * @throws DeliveryFailedException.Temporary when sending may work later
     * @throws DeliveryFailedException.Permanent when the server refused the credentials or the recipient
     */
    void send(EmailChannel channel, OutgoingEmail email);
}

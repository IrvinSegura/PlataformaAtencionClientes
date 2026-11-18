package com.irvinsegura.supportchat.channels.infrastructure.mail;

import com.irvinsegura.supportchat.channels.domain.model.InvalidEmailChannelException;
import com.irvinsegura.supportchat.channels.domain.port.MailServerPolicy;
import com.irvinsegura.supportchat.shared.infrastructure.NetworkAddresses;
import java.net.UnknownHostException;

/**
 * Every address a mail server's name resolves to must be public, checked when the mailbox is saved and again before
 * each connection. As with action webhooks, a name that changes between the check and the connection is a remaining
 * gap that an egress proxy closes.
 *
 * @param allowPrivateNetwork true only for tests and local development, to reach a mail server on the same machine
 */
public record NetworkMailServerPolicy(boolean allowPrivateNetwork) implements MailServerPolicy {

    @Override
    public void check(String host) {
        if (allowPrivateNetwork) {
            return;
        }
        try {
            if (!NetworkAddresses.allPublic(host)) {
                throw new InvalidEmailChannelException("Mail servers cannot be private, local or reserved network "
                        + "addresses");
            }
        } catch (UnknownHostException e) {
            throw new InvalidEmailChannelException("The mail server " + host + " does not resolve");
        }
    }
}

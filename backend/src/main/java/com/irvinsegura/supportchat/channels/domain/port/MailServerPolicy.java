package com.irvinsegura.supportchat.channels.domain.port;

/**
 * Mail servers must be public internet hosts, so a workspace cannot point the platform at its own network.
 */
public interface MailServerPolicy {

    /**
     * @throws com.irvinsegura.supportchat.channels.domain.model.InvalidEmailChannelException when the host is not
     *                                                                                         allowed
     */
    void check(String host);
}

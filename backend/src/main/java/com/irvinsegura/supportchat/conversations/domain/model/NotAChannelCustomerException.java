package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * Web visitors are anonymous: there is nobody to keep a profile of.
 */
public class NotAChannelCustomerException extends DomainException {

    public NotAChannelCustomerException() {
        super("NOT_A_CHANNEL_CUSTOMER", "Only customers who write through a channel such as WhatsApp have a profile");
    }
}

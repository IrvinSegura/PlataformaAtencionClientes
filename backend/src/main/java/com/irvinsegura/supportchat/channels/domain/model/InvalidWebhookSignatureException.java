package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnauthenticatedException;

/**
 * A webhook delivery whose signature does not match: not from Meta, or altered on the way.
 */
public class InvalidWebhookSignatureException extends UnauthenticatedException {

    public InvalidWebhookSignatureException() {
        super("INVALID_WEBHOOK_SIGNATURE", "The webhook signature is not valid");
    }
}

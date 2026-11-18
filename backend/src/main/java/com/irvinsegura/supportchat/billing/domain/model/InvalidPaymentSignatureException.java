package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnauthenticatedException;

/**
 * A payment notification whose signature does not match, or that is too old to trust: not from the provider, altered
 * or replayed.
 */
public class InvalidPaymentSignatureException extends UnauthenticatedException {

    public InvalidPaymentSignatureException() {
        super("INVALID_WEBHOOK_SIGNATURE", "The webhook signature is not valid");
    }
}

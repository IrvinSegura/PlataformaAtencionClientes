package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnavailableException;

/**
 * The payment provider could not be reached, or refused a request.
 */
public class PaymentProviderUnavailableException extends UnavailableException {

    public PaymentProviderUnavailableException(String message) {
        super("PAYMENTS_UNAVAILABLE", message);
    }
}

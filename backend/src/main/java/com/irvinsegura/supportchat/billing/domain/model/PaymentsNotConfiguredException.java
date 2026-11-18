package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * This installation has no payment provider keys, so plans cannot be bought.
 */
public class PaymentsNotConfiguredException extends DomainException {

    public PaymentsNotConfiguredException() {
        super("PAYMENTS_NOT_CONFIGURED", "Payments are not configured on this platform");
    }
}

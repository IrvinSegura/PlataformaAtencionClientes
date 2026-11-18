package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The recharge exists but cannot be bought: the plan bills past its limit instead, or the payment provider has no
 * price for it.
 */
public class TopUpNotForSaleException extends DomainException {

    public TopUpNotForSaleException(String message) {
        super("TOP_UP_NOT_FOR_SALE", message);
    }
}

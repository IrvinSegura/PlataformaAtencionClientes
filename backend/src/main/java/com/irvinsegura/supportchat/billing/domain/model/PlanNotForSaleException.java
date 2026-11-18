package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The plan exists but cannot be bought: it is free, it is the current one, or the payment provider has no prices
 * for it.
 */
public class PlanNotForSaleException extends DomainException {

    public PlanNotForSaleException(String message) {
        super("PLAN_NOT_FOR_SALE", message);
    }
}

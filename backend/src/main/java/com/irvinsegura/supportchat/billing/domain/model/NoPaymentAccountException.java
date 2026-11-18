package com.irvinsegura.supportchat.billing.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * Payment details and invoices exist only after a first plan was bought.
 */
public class NoPaymentAccountException extends DomainException {

    public NoPaymentAccountException() {
        super("NO_PAYMENT_ACCOUNT", "The workspace has not bought a plan yet");
    }
}

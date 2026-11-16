package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * An account is created, or the terms accepted, without agreeing to the terms of service in force.
 */
public class TermsNotAcceptedException extends DomainException {

    public TermsNotAcceptedException(String message) {
        super("TERMS_NOT_ACCEPTED", message);
    }
}

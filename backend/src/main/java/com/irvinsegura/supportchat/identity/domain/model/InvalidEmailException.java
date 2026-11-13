package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidEmailException extends DomainException {

    public InvalidEmailException() {
        super("INVALID_EMAIL", "Not a valid email address");
    }
}

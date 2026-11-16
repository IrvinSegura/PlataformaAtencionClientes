package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidProfileException extends DomainException {

    public InvalidProfileException(String message) {
        super("INVALID_PROFILE", message);
    }
}

package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class WeakPasswordException extends DomainException {

    public WeakPasswordException(String message) {
        super("WEAK_PASSWORD", message);
    }
}

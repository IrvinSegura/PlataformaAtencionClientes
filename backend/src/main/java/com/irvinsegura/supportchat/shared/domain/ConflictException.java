package com.irvinsegura.supportchat.shared.domain;

/**
 * The request clashes with the current state, e.g. a duplicate (409).
 */
public abstract class ConflictException extends DomainException {

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}

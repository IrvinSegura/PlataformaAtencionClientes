package com.irvinsegura.supportchat.shared.domain;

/**
 * What the caller asked for does not exist, or belongs to another workspace, which must look the same (404).
 */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}

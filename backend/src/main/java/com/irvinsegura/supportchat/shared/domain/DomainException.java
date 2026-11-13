package com.irvinsegura.supportchat.shared.domain;

/**
 * Base type for business rule violations, answered with 422. The code is a stable identifier API clients can rely
 * on; the subclasses in this package map to other statuses.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

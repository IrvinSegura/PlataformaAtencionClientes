package com.irvinsegura.supportchat.shared.domain;

/**
 * Credentials or tokens were missing, wrong or expired (401). Messages never say which part was wrong.
 */
public abstract class UnauthenticatedException extends DomainException {

    protected UnauthenticatedException(String code, String message) {
        super(code, message);
    }
}

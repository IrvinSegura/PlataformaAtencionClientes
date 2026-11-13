package com.irvinsegura.supportchat.shared.domain;

/**
 * The caller is signed in but their role does not allow this action on this target (403). For rules that depend on
 * the data, which role annotations on endpoints cannot express, e.g. an admin removing the owner.
 */
public abstract class ForbiddenException extends DomainException {

    protected ForbiddenException(String code, String message) {
        super(code, message);
    }
}

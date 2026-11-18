package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The integration's settings break a rule, e.g. a store outside myshopify.com.
 */
public class InvalidIntegrationException extends DomainException {

    public InvalidIntegrationException(String message) {
        super("INVALID_INTEGRATION", message);
    }
}

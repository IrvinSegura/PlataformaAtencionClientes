package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The webhook address may not be called from the platform, e.g. it points into a private network.
 */
public class EndpointNotAllowedException extends DomainException {

    public EndpointNotAllowedException(String message) {
        super("ENDPOINT_NOT_ALLOWED", message);
    }
}

package com.irvinsegura.supportchat.shared.domain;

/**
 * A service the request depends on (a payment provider, a store's API) cannot be reached right now: answered with
 * 503, so clients know a retry later may work.
 */
public abstract class UnavailableException extends DomainException {

    protected UnavailableException(String code, String message) {
        super(code, message);
    }
}

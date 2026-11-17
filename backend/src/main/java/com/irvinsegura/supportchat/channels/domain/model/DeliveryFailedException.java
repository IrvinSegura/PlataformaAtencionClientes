package com.irvinsegura.supportchat.channels.domain.model;

/**
 * A reply could not be sent on a channel.
 * <p>
 * {@link Temporary} failures may pass (network, rate limits, the provider's own errors) and are left to propagate,
 * so the event is delivered again; {@link Permanent} ones (bad credentials, a refused recipient) would fail the same
 * way again and are only logged.
 */
public abstract sealed class DeliveryFailedException extends RuntimeException {

    protected DeliveryFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class Temporary extends DeliveryFailedException {

        public Temporary(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class Permanent extends DeliveryFailedException {

        public Permanent(String message) {
            super(message, null);
        }
    }
}

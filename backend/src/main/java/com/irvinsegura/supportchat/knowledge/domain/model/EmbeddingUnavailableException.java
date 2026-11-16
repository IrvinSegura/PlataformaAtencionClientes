package com.irvinsegura.supportchat.knowledge.domain.model;

/**
 * The embedding provider could not be reached or refused for now (timeout, rate limit, outage). Worth retrying
 * later, unlike a document that cannot be read.
 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

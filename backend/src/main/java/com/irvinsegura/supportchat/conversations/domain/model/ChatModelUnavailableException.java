package com.irvinsegura.supportchat.conversations.domain.model;

/**
 * The language model provider could not answer now (timeout, rate limit, overload, outage).
 */
public class ChatModelUnavailableException extends RuntimeException {

    public ChatModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

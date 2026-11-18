package com.irvinsegura.supportchat.actions.domain.model;

/**
 * The language model's arguments do not fit the action. The message is written for the model, which can ask the
 * customer for what is missing and try again; nothing is sent to the business.
 */
public class InvalidActionArgumentsException extends RuntimeException {

    public InvalidActionArgumentsException(String message) {
        super(message);
    }
}

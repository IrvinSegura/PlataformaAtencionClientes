package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The action's settings break a rule, e.g. a name the model API would reject.
 */
public class InvalidActionException extends DomainException {

    public InvalidActionException(String message) {
        super("INVALID_ACTION", message);
    }
}

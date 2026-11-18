package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * Every action is sent to the model with each question, so their number is bounded.
 */
public class ActionLimitReachedException extends DomainException {

    public ActionLimitReachedException() {
        super("ACTION_LIMIT_REACHED", "A workspace has at most %d actions".formatted(Action.MAX_PER_WORKSPACE));
    }
}

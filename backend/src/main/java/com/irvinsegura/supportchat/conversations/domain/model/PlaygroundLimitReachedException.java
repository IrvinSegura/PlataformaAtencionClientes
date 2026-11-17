package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

/**
 * The plan's answers for the month are used up; playground answers count like customers' ones.
 */
public class PlaygroundLimitReachedException extends ConflictException {

    public PlaygroundLimitReachedException() {
        super("PLAN_LIMIT_REACHED", "The plan's answers for this month are used up; upgrade the plan to keep testing");
    }
}

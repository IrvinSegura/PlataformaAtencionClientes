package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

/**
 * A proposed action is confirmed or cancelled once.
 */
public class ActionAlreadyHandledException extends ConflictException {

    public ActionAlreadyHandledException() {
        super("ACTION_ALREADY_HANDLED", "This action was already confirmed, cancelled or expired");
    }
}

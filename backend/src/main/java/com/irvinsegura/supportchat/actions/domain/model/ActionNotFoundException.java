package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * No action with that id in the workspace.
 */
public class ActionNotFoundException extends NotFoundException {

    public ActionNotFoundException() {
        super("ACTION_NOT_FOUND", "The action does not exist");
    }
}

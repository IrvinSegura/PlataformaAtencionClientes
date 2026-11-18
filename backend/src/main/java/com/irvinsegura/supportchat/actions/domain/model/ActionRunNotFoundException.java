package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * The run does not exist or belongs to another conversation, which must look the same.
 */
public class ActionRunNotFoundException extends NotFoundException {

    public ActionRunNotFoundException() {
        super("ACTION_RUN_NOT_FOUND", "There is no such action in this conversation");
    }
}

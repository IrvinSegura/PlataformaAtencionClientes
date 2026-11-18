package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

/**
 * Tool names must be unique, or the model could not tell two actions apart.
 */
public class DuplicateActionNameException extends ConflictException {

    public DuplicateActionNameException() {
        super("ACTION_NAME_TAKEN", "Another action already has that name");
    }
}

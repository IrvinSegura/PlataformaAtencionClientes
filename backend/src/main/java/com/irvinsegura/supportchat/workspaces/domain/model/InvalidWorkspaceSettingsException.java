package com.irvinsegura.supportchat.workspaces.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidWorkspaceSettingsException extends DomainException {

    public InvalidWorkspaceSettingsException(String message) {
        super("INVALID_WORKSPACE_SETTINGS", message);
    }
}

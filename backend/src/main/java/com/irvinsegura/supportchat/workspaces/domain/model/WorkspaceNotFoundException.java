package com.irvinsegura.supportchat.workspaces.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class WorkspaceNotFoundException extends NotFoundException {

    public WorkspaceNotFoundException() {
        super("WORKSPACE_NOT_FOUND", "Workspace not found");
    }
}

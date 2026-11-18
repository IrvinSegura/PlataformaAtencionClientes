package com.irvinsegura.supportchat.platform.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class PlatformWorkspaceNotFoundException extends NotFoundException {

    public PlatformWorkspaceNotFoundException() {
        super("WORKSPACE_NOT_FOUND", "Workspace not found");
    }
}

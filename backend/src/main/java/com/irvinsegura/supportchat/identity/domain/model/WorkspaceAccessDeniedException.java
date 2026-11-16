package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.ForbiddenException;

public class WorkspaceAccessDeniedException extends ForbiddenException {

    public WorkspaceAccessDeniedException() {
        super("WORKSPACE_ACCESS_DENIED", "You are not a member of this workspace");
    }
}

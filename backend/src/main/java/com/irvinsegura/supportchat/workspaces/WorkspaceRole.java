package com.irvinsegura.supportchat.workspaces;

/**
 * What a member may do in a workspace.
 */
public enum WorkspaceRole {
    // Everything, including settings; the one who created the workspace
    OWNER,
    // Manages the knowledge base, the widget and the agents of the team
    ADMIN,
    // Answers conversations and reads the knowledge base
    AGENT
}

package com.irvinsegura.supportchat.workspaces;

import java.time.Instant;

/**
 * Why and since when whoever runs the platform suspended a workspace: its assistant answers nothing until reinstated.
 *
 * @param reason what the workspace's members are told
 */
public record WorkspaceSuspension(String reason, Instant suspendedAt) {
}

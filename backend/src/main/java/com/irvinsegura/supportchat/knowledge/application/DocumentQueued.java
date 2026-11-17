package com.irvinsegura.supportchat.knowledge.application;

import java.util.UUID;

/**
 * A document waits for ingestion. Stored in the outbox with the transaction that queued it, so it is processed even
 * if the instance stops right after the upload.
 */
public record DocumentQueued(UUID documentId, UUID workspaceId) {
}

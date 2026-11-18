package com.irvinsegura.supportchat.actions.domain.port;

import com.irvinsegura.supportchat.actions.domain.model.ActionRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Action runs of the workspace bound to the current transaction.
 */
public interface ActionRunRepository {

    void insert(ActionRun run);

    void update(ActionRun run);

    /**
     * Locks the run until the transaction ends, so it is confirmed or cancelled once.
     */
    Optional<ActionRun> findByIdForUpdate(UUID id);

    /**
     * Oldest first.
     */
    List<ActionRun> byConversation(UUID conversationId);
}

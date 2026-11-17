package com.irvinsegura.supportchat.channels.domain.port;

import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.EmailThread;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The mailbox of the workspace bound to the current transaction, and its threads with each customer.
 */
public interface EmailChannelRepository {

    Optional<EmailChannel> find();

    void save(EmailChannel channel);

    /**
     * Saves only how far the mailbox was read and how the last read went, so it never undoes settings an operator
     * saved while the mailbox was being read.
     */
    void saveProgress(EmailChannel channel);

    void delete(UUID workspaceId);

    /**
     * The workspaces with a mailbox, before any workspace is bound.
     */
    List<UUID> workspacesWithMailbox();

    Optional<EmailThread> findThread(String contactAddress);

    void saveThread(UUID workspaceId, EmailThread thread, Instant now);
}

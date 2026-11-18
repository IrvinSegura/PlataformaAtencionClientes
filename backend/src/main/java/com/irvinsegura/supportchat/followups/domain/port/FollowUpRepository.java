package com.irvinsegura.supportchat.followups.domain.port;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.followups.domain.model.FollowUp;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpStatus;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Follow-ups and their templates, of the workspace bound to the current transaction; except
 * {@link #workspacesWithDue}, which runs before any workspace is bound.
 */
public interface FollowUpRepository {

    List<FollowUpTemplate> templates();

    Optional<FollowUpTemplate> findTemplate(UUID id);

    /**
     * Inserts the template or replaces the saved one.
     *
     * @return false when another template of the workspace already has its name
     */
    boolean saveTemplate(FollowUpTemplate template);

    /**
     * @return whether there was such a template; follow-ups scheduled from it keep their text
     */
    boolean deleteTemplate(UUID id);

    /**
     * Saves a new follow-up with who it is for and, while it is scheduled, queues it for the job.
     */
    void insert(FollowUpRow row);

    /**
     * Saves the follow-up's new state and takes it off the job's queue once it is no longer scheduled.
     */
    void update(FollowUp followUp);

    Optional<FollowUp> findForUpdate(UUID id);

    /**
     * The follow-up that went out as this message, locked until the transaction ends.
     */
    Optional<FollowUp> findByMessageForUpdate(UUID messageId);

    /**
     * The oldest scheduled follow-up due by now, locked until the transaction ends; one another instance is sending
     * is skipped.
     */
    Optional<FollowUp> claimDue(Instant now);

    /**
     * Workspaces with a follow-up due by now, across all workspaces.
     */
    List<UUID> workspacesWithDue(Instant now);

    /**
     * The follow-ups of a conversation, soonest first.
     */
    List<FollowUpRow> ofConversation(UUID conversationId);

    /**
     * @param status only follow-ups in this state, or null for all of them; scheduled ones come soonest first, the
     *               rest most recent first
     */
    FollowUpPage page(FollowUpStatus status, int page, int size);

    /**
     * A follow-up with who it is for.
     */
    record FollowUpRow(FollowUp followUp, String templateName, ConversationChannel channel, String contactId,
            String contactName) {
    }

    record FollowUpPage(List<FollowUpRow> followUps, long total) {
    }
}

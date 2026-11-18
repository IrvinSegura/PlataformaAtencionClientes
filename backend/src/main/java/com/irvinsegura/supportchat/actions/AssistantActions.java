package com.irvinsegura.supportchat.actions;

import java.util.List;
import java.util.UUID;

/**
 * What the assistant may do in the business's systems, for the workspace bound to the current code. Methods that call
 * a webhook must run outside any transaction, so no database connection is held while the business answers.
 */
public interface AssistantActions {

    /**
     * Enabled actions, to offer to the language model as tools.
     */
    List<ActionDefinition> available();

    /**
     * Runs the action the model asked for, or records it as waiting for the visitor's confirmation when it changes
     * something. Problems the model can react to (an unknown action, invalid arguments, an error of the business's
     * system) are reported in the outcome rather than thrown.
     */
    ActionOutcome invoke(ActionInvocation invocation);

    /**
     * The visitor confirmed a proposed action: it runs now, unless the confirmation expired.
     *
     * @param channel   the conversation's channel, sent to the business like in {@link ActionInvocation}
     * @param contactId the verified contact, or null
     * @throws com.irvinsegura.supportchat.actions.domain.model.ActionRunNotFoundException when the run does not belong
     *         to the conversation
     * @throws com.irvinsegura.supportchat.actions.domain.model.ActionAlreadyHandledException when it was already
     *         confirmed or cancelled
     */
    ActionOutcome confirm(UUID runId, UUID conversationId, String channel, String contactId);

    /**
     * The visitor rejected a proposed action.
     */
    ActionOutcome cancel(UUID runId, UUID conversationId);

    /**
     * Everything the assistant ran or proposed in a conversation, oldest first.
     */
    List<ActionRunView> runsOf(UUID conversationId);
}

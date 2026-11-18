package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.domain.model.EmailActionReply;
import java.util.UUID;

/**
 * A new customer mail was read from the mailbox; it is answered once the read is saved.
 *
 * @param contactId   the sender's address
 * @param text        what they asked
 * @param actionReply their answer to an action the assistant proposed, or null
 */
public record EmailReceived(UUID workspaceId, String contactId, String contactName, String text,
        EmailActionReply actionReply) {
}

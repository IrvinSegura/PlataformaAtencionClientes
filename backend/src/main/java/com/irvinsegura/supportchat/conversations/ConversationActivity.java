package com.irvinsegura.supportchat.conversations;

import java.time.Instant;

/**
 * How busy the workspace bound to the current code is, for those who run the platform: counts only, never what was
 * said.
 */
public interface ConversationActivity {

    Activity since(Instant since);

    /**
     * @param conversations  conversations started since the given moment, through every channel
     * @param lastActivityAt the latest message of any conversation, or null if it never had one
     */
    record Activity(long conversations, Instant lastActivityAt) {
    }
}

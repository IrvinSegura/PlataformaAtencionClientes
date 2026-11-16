package com.irvinsegura.supportchat.knowledge;

import java.util.UUID;

/**
 * Lets the team teach the assistant the answer to a question it struggled with, without writing a document: the
 * question and the answer become a small document of the knowledge base, found by the next customer who asks the same.
 */
public interface TaughtAnswers {

    /**
     * Adds the answer to the current workspace's knowledge base; it is searchable once processed, in a few seconds.
     *
     * @return the new document's id
     */
    UUID teach(String question, String answer, UUID taughtBy);
}

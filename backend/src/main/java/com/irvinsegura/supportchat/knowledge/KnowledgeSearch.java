package com.irvinsegura.supportchat.knowledge;

import java.util.List;

/**
 * Finds the passages of the current workspace's knowledge base that best answer a question.
 */
public interface KnowledgeSearch {

    /**
     * Best matches first. Must run with a workspace bound; it searches only that workspace.
     */
    List<KnowledgeMatch> search(String question, int limit);

    /**
     * Similarity from which a match is usually about what the question asks, for the embedding model in use.
     */
    double relatedSimilarity();

    /**
     * A value that changes whenever a document of the current workspace is added, processed again, renamed or
     * deleted, so whatever was worked out from the knowledge base can tell it is out of date. Must run with a
     * workspace bound.
     */
    String revision();
}

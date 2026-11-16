package com.irvinsegura.supportchat.knowledge;

import java.util.List;

/**
 * Groups short texts, such as questions customers asked, by what they are about, with the embedding model of the
 * knowledge base.
 */
public interface TopicGrouping {

    /**
     * Each group lists indexes into {@code texts}. A text joins the group of the first earlier text close enough in
     * meaning, so put the most important texts first: they lead their groups. Groups come in the order of their
     * leaders.
     */
    List<List<Integer>> group(List<String> texts);
}

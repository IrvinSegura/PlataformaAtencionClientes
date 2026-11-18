package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceSelectionAndCitationsTest {

    private final SourceSelection selection = new SourceSelection(0.4, 2);

    @Test
    void keepsStrongCandidatesInRankOrderAndNumbersThem() {
        List<Source> selected = selection.select(List.of(
                candidate("weak", 0.1, false),
                candidate("strong", 0.9, false),
                candidate("keyword", 0.25, true),
                candidate("extra", 0.8, false)));

        assertThat(selected).extracting(Source::content).containsExactly("strong", "keyword");
        assertThat(selected).extracting(Source::number).containsExactly(1, 2);
    }

    @Test
    void aKeywordMatchStillNeedsSomeSemanticSimilarity() {
        assertThat(selection.select(List.of(candidate("noise", 0.1, true)))).isEmpty();
        assertThat(selection.select(List.of(candidate("keyword only", null, true)))).hasSize(1);
    }

    @Test
    void citationsListOnlyExistingSourcesOnceInOrder() {
        List<Source> sources = selection.select(List.of(candidate("a", 0.9, false), candidate("b".repeat(300), 0.9,
                false)));

        List<Citation> citations = Citations.citedIn("Yes [2]. Also [1][2] and [7].", sources);

        assertThat(citations).extracting(Citation::number).containsExactly(1, 2);
        assertThat(citations.get(1).excerpt()).hasSize(201).endsWith("…");
    }

    private static Source candidate(String content, Double similarity, boolean keywordMatch) {
        return new Source(0, UUID.randomUUID(), UUID.randomUUID(), "Doc", content, similarity, keywordMatch);
    }
}

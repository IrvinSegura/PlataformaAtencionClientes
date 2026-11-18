package com.irvinsegura.supportchat.knowledge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(200, 40);

    @Test
    void shortTextIsOneChunk() {
        assertThat(chunker.split("Our store opens at 9 am.\n\nWe close at 6 pm."))
                .containsExactly("Our store opens at 9 am.\n\nWe close at 6 pm.");
    }

    @Test
    void blankOrMissingTextHasNoChunks() {
        assertThat(chunker.split(null)).isEmpty();
        assertThat(chunker.split(" \n\t\n ")).isEmpty();
    }

    @Test
    void noChunkIsLongerThanTheLimit() {
        String text = "This sentence talks about refunds and shipping times. ".repeat(60);

        List<String> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(200));
    }

    @Test
    void paragraphsAreKeptWholeWhenTheyFit() {
        String first = "a".repeat(120);
        String second = "b".repeat(120);

        assertThat(new TextChunker(200, 0).split(first + "\n\n" + second)).containsExactly(first, second);
    }

    @Test
    void consecutiveChunksOverlapSoFactsAtTheBorderStayFindable() {
        List<String> paragraphs = List.of(
                "Returns are accepted within thirty days of delivery for unused items in their box.",
                "Refunds go back to the original payment method within five business days after we get the item.",
                "Exchanges for a different size are free and ship as soon as the return is scanned.");

        List<String> chunks = chunker.split(String.join("\n\n", paragraphs));

        assertThat(chunks).hasSizeGreaterThan(1);
        String endOfFirst = chunks.get(0).substring(chunks.get(0).length() - 20);
        assertThat(chunks.get(1)).contains(endOfFirst.substring(endOfFirst.indexOf(' ') + 1));
    }

    @Test
    void aWordLongerThanTheLimitIsCutRatherThanLost() {
        String word = "x".repeat(450);

        List<String> chunks = chunker.split(word);

        assertThat(String.join("", chunks)).isEqualTo(word);
    }

    @Test
    void controlCharactersAndRepeatedSpacesAreCleanedUp() {
        assertThat(chunker.split("Hello\u0000   world\r\n\r\n\r\n\r\nBye\t\tnow"))
                .containsExactly("Hello world\n\nBye now");
    }

    @Test
    void rejectsSettingsThatCannotWork() {
        assertThatThrownBy(() -> new TextChunker(50, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(200, 150)).isInstanceOf(IllegalArgumentException.class);
    }
}

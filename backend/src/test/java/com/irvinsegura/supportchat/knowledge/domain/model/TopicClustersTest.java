package com.irvinsegura.supportchat.knowledge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicClustersTest {

    @Test
    void textsCloseToALeaderJoinItsGroupAndTheRestStartTheirOwn() {
        List<float[]> vectors = List.of(unit(1, 0, 0), unit(0, 1, 0), unit(0.9f, 0.1f, 0), unit(0, 0, 1),
                unit(0.1f, 0.95f, 0));

        assertThat(TopicClusters.group(vectors, 0.8)).containsExactly(List.of(0, 2), List.of(1, 4), List.of(3));
    }

    @Test
    void aTextJoinsTheLeaderItResemblesMost() {
        List<float[]> vectors = List.of(unit(1, 0, 0), unit(0, 1, 0), unit(0.6f, 0.8f, 0));

        assertThat(TopicClusters.group(vectors, 0.5)).containsExactly(List.of(0), List.of(1, 2));
    }

    @Test
    void comparingWithTheLeaderOnlyKeepsAChainFromMergingTopics() {
        // b resembles a, c resembles b, but c and a have little in common
        float[] a = unit(1, 0, 0);
        float[] b = unit(0.8f, 0.6f, 0);
        float[] c = unit(0.28f, 0.96f, 0);

        assertThat(TopicClusters.group(List.of(a, b, c), 0.75)).containsExactly(List.of(0, 1), List.of(2));
    }

    @Test
    void nothingToGroup() {
        assertThat(TopicClusters.group(List.of(), 0.5)).isEmpty();
    }

    private static float[] unit(float... values) {
        double norm = 0;
        for (float value : values) {
            norm += value * value;
        }
        float[] unit = values.clone();
        for (int i = 0; i < unit.length; i++) {
            unit[i] /= (float) Math.sqrt(norm);
        }
        return unit;
    }
}

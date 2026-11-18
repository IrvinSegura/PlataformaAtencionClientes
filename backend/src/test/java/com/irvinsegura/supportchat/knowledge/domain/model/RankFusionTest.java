package com.irvinsegura.supportchat.knowledge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.knowledge.domain.model.RankFusion.FusedRank;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankFusionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void anItemFoundByBothRetrieversBeatsOneFoundByOnlyOne() {
        List<FusedRank> fused = RankFusion.fuse(List.of(List.of(a, b), List.of(c, b)), RankFusion.DEFAULT_K);

        assertThat(fused).extracting(FusedRank::id).first().isEqualTo(b);
        assertThat(fused).hasSize(3);
    }

    @Test
    void scoresFollowTheReciprocalRankFormula() {
        List<FusedRank> fused = RankFusion.fuse(List.of(List.of(a, b)), 60);

        assertThat(fused.get(0).score()).isEqualTo(1.0 / 61);
        assertThat(fused.get(1).score()).isEqualTo(1.0 / 62);
    }

    @Test
    void noRankingsMeanNoResults() {
        assertThat(RankFusion.fuse(List.of(List.of(), List.of()), 60)).isEmpty();
    }
}

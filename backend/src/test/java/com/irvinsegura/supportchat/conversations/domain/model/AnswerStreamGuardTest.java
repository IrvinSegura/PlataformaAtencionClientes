package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerStreamGuardTest {

    @Test
    void aNormalAnswerIsReleasedAsSoonAsItCannotBeTheMarker() {
        AnswerStreamGuard guard = new AnswerStreamGuard();

        assertThat(guard.accept("We ")).isEqualTo("We ");
        assertThat(guard.accept("open at 9 [1].")).isEqualTo("open at 9 [1].");
        assertThat(guard.finish()).isEmpty();
        assertThat(guard.isNoAnswer()).isFalse();
        assertThat(guard.fullText()).isEqualTo("We open at 9 [1].");
    }

    @Test
    void theMarkerSplitAcrossPiecesNeverReachesTheVisitor() {
        AnswerStreamGuard guard = new AnswerStreamGuard();

        String shown = String.join("", List.of(guard.accept(" NO_"), guard.accept("ANS"), guard.accept("WER"),
                guard.finish()));

        assertThat(shown).isEmpty();
        assertThat(guard.isNoAnswer()).isTrue();
    }

    @Test
    void textThatOnlyStartsLikeTheMarkerIsReleasedWhole() {
        AnswerStreamGuard guard = new AnswerStreamGuard();

        assertThat(guard.accept("NO")).isEmpty();
        assertThat(guard.accept("T available on Sundays.")).isEqualTo("NOT available on Sundays.");
        assertThat(guard.isNoAnswer()).isFalse();
    }

    @Test
    void aShortAnswerHeldUntilTheEndIsReleasedByFinish() {
        AnswerStreamGuard guard = new AnswerStreamGuard();

        assertThat(guard.accept("NO")).isEmpty();
        assertThat(guard.finish()).isEqualTo("NO");
        assertThat(guard.isNoAnswer()).isFalse();
    }

    @Test
    void anEmptyAnswerCountsAsNoAnswer() {
        AnswerStreamGuard guard = new AnswerStreamGuard();

        assertThat(guard.finish()).isEmpty();
        assertThat(guard.isNoAnswer()).isTrue();
    }
}

package com.irvinsegura.supportchat.knowledge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeywordQueryTest {

    @Test
    void keepsMeaningfulTermsJoinedWithOr() {
        assertThat(KeywordQuery.from("¿Cuál es el horario de la tienda?")).contains("horario | tienda");
    }

    @Test
    void keepsShortTermsWithDigitsSuchAsModelNumbers() {
        assertThat(KeywordQuery.from("plan 5G x2")).contains("plan | 5g | x2");
    }

    @Test
    void operatorsAndPunctuationCannotReachTheQuery() {
        assertThat(KeywordQuery.from("refund' & !(price) | :*")).contains("refund | price");
    }

    @Test
    void repeatsAreRemoved() {
        assertThat(KeywordQuery.from("shipping shipping SHIPPING")).contains("shipping");
    }

    @Test
    void aQuestionOfOnlyStopWordsHasNoQuery() {
        assertThat(KeywordQuery.from("hola, ¿qué es?")).isEmpty();
        assertThat(KeywordQuery.from(null)).isEmpty();
    }
}

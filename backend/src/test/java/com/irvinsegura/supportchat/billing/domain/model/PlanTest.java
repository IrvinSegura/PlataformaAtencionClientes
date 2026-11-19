package com.irvinsegura.supportchat.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.billing.UsageMetric;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanTest {

    private static final Plan FREE = new Plan("FREE", "Gratis", BigDecimal.ZERO,
            Map.of(UsageMetric.ANSWERS, 100L), Map.of());
    private static final Plan STARTER = new Plan("STARTER", "Inicial", new BigDecimal("29"),
            Map.of(UsageMetric.ANSWERS, 2000L, UsageMetric.ACTION_RUNS, 500L),
            Map.of(UsageMetric.ANSWERS, new BigDecimal("0.02")));

    @Test
    void aMetricWithoutIncludedQuantityIsUnlimited() {
        assertThat(FREE.allows(UsageMetric.INPUT_TOKENS, Long.MAX_VALUE - 1)).isTrue();
        assertThat(FREE.limit(UsageMetric.INPUT_TOKENS)).isEmpty();
        assertThat(FREE.overage(UsageMetric.INPUT_TOKENS, 5_000)).isZero();
    }

    @Test
    void withoutOveragePriceAMetricStopsAtItsIncludedQuantity() {
        assertThat(FREE.allows(UsageMetric.ANSWERS, 99)).isTrue();
        assertThat(FREE.allows(UsageMetric.ANSWERS, 100)).isFalse();
        assertThat(STARTER.allows(UsageMetric.ACTION_RUNS, 500)).isFalse();
    }

    @Test
    void aRechargeRaisesALimitedMetricOnly() {
        Plan recharged = FREE.withExtra(Map.of(UsageMetric.ANSWERS, 500L, UsageMetric.INPUT_TOKENS, 10L));

        assertThat(recharged.limit(UsageMetric.ANSWERS)).contains(600L);
        assertThat(recharged.allows(UsageMetric.ANSWERS, 599)).isTrue();
        assertThat(recharged.allows(UsageMetric.ANSWERS, 600)).isFalse();
        // Unlimited stays unlimited
        assertThat(recharged.limit(UsageMetric.INPUT_TOKENS)).isEmpty();
        assertThat(FREE.withExtra(Map.of())).isSameAs(FREE);
        assertThat(new TopUp("ANSWERS_500", "500 respuestas", UsageMetric.ANSWERS, 500, BigDecimal.TEN).fits(FREE))
                .isTrue();
        assertThat(new TopUp("ANSWERS_500", "500 respuestas", UsageMetric.ANSWERS, 500, BigDecimal.TEN)
                .fits(STARTER)).isFalse();
    }

    @Test
    void withOveragePriceAMetricKeepsWorkingAndEachExtraUnitIsCharged() {
        assertThat(STARTER.allows(UsageMetric.ANSWERS, 10_000)).isTrue();
        assertThat(STARTER.overage(UsageMetric.ANSWERS, 2_150)).isEqualTo(150);
        assertThat(STARTER.overageCost(UsageMetric.ANSWERS, 2_150)).isEqualByComparingTo("3.00");
        assertThat(STARTER.overageCost(UsageMetric.ANSWERS, 1_000)).isEqualByComparingTo("0");
        assertThat(STARTER.overageCost(UsageMetric.ACTION_RUNS, 900)).isEqualByComparingTo("0");
    }

    @Test
    void onlyAPlanWithoutAnyChargeIsFree() {
        assertThat(FREE.isFree()).isTrue();
        assertThat(STARTER.isFree()).isFalse();
        assertThat(new Plan("METERED", "Por uso", BigDecimal.ZERO, Map.of(),
                Map.of(UsageMetric.ANSWERS, BigDecimal.ONE)).isFree()).isFalse();
    }

    @Test
    void refusesInvalidTerms() {
        assertThatThrownBy(() -> new Plan("starter", "x", BigDecimal.ONE, Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plan("BAD", "x", new BigDecimal("-1"), Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plan("BAD", "x", BigDecimal.ONE, Map.of(UsageMetric.ANSWERS, -1L), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plan("BAD", "x", BigDecimal.ONE, Map.of(),
                Map.of(UsageMetric.ANSWERS, new BigDecimal("-0.1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCatalogFallsBackToTheFreePlanAndListsPlansByPrice() {
        PlanCatalog catalog = new PlanCatalog(List.of(STARTER, FREE), "FREE");

        assertThat(catalog.resolve("GONE")).isEqualTo(FREE);
        assertThat(catalog.resolve(null)).isEqualTo(FREE);
        assertThat(catalog.all()).extracting(Plan::code).containsExactly("FREE", "STARTER");
        assertThatThrownBy(() -> new PlanCatalog(List.of(STARTER), "STARTER"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

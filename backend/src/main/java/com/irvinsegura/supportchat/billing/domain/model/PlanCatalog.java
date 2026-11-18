package com.irvinsegura.supportchat.billing.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The plans on offer, cheapest first. A workspace with no subscription, or one that stopped paying, is on the free
 * plan.
 */
public final class PlanCatalog {

    private final Map<String, Plan> plans;
    private final Plan free;
    private final List<Plan> ordered;

    public PlanCatalog(List<Plan> plans, String freePlanCode) {
        Map<String, Plan> byCode = new LinkedHashMap<>();
        for (Plan plan : plans) {
            if (byCode.put(plan.code(), plan) != null) {
                throw new IllegalArgumentException("Two plans share the code " + plan.code());
            }
        }
        this.plans = Map.copyOf(byCode);
        this.free = Optional.ofNullable(byCode.get(Objects.requireNonNull(freePlanCode)))
                .orElseThrow(() -> new IllegalArgumentException("The free plan " + freePlanCode + " is not defined"));
        if (!free.isFree()) {
            throw new IllegalArgumentException("The free plan " + freePlanCode + " must cost nothing");
        }
        this.ordered = byCode.values().stream()
                .sorted((left, right) -> left.monthlyPrice().compareTo(right.monthlyPrice()))
                .toList();
    }

    public Plan free() {
        return free;
    }

    public Optional<Plan> find(String code) {
        return Optional.ofNullable(code == null ? null : plans.get(code));
    }

    /**
     * A plan that is no longer offered keeps its subscribers working on the free plan's terms.
     */
    public Plan resolve(String code) {
        return find(code).orElse(free);
    }

    public List<Plan> all() {
        return ordered;
    }
}

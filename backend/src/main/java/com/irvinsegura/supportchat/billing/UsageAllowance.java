package com.irvinsegura.supportchat.billing;

/**
 * What the plan of the workspace bound to the current code still lets it use this month. Asked before the work that
 * consumes it, inside its transaction. Concurrent requests may pass the limit by a unit or two; the limit protects
 * against runaway costs, not against one extra answer.
 */
public interface UsageAllowance {

    /**
     * Whether one more unit of the metric fits the plan: it is unlimited, below its included quantity, or billed
     * past it.
     */
    boolean allows(UsageMetric metric);
}

package com.irvinsegura.supportchat.billing;

import java.time.Instant;

/**
 * Records consumption of the workspace bound to the current code. Call it inside the transaction of the work being
 * measured: the usage is then saved if and only if that work is.
 */
public interface UsageMeter {

    /**
     * @param dimension what the quantity belongs to, e.g. the model for tokens; empty when nothing applies
     * @param quantity  zero or more; zero records nothing
     */
    void record(Instant at, UsageMetric metric, String dimension, long quantity);
}

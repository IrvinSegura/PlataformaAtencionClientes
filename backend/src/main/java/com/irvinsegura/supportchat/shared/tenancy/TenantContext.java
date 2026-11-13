package com.irvinsegura.supportchat.shared.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The workspace (tenant) the current code runs for. It is held in a scoped value, so it can neither leak into the
 * next task of a pooled thread nor change halfway through: it exists exactly while {@link #runAs} or
 * {@link #callAs} runs.
 * <p>
 * Every transaction copies it into the database session, where row-level security policies read it. Code that
 * touches workspace data must therefore bind it <em>before</em> a transaction starts; without it those tables look
 * empty and reject writes.
 */
public final class TenantContext {

    private static final ScopedValue<UUID> WORKSPACE = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static Optional<UUID> currentWorkspaceId() {
        return WORKSPACE.isBound() ? Optional.of(WORKSPACE.get()) : Optional.empty();
    }

    public static UUID requireWorkspaceId() {
        return currentWorkspaceId().orElseThrow(() -> new IllegalStateException("No workspace is bound"));
    }

    public static void runAs(UUID workspaceId, Runnable action) {
        ScopedValue.where(WORKSPACE, Objects.requireNonNull(workspaceId)).run(action);
    }

    public static <T> T callAs(UUID workspaceId, Supplier<T> action) {
        return ScopedValue.where(WORKSPACE, Objects.requireNonNull(workspaceId)).call(action::get);
    }

    /**
     * Variant for code that throws checked exceptions, such as a servlet filter chain.
     */
    public static <T, X extends Throwable> T callChecked(UUID workspaceId, ScopedValue.CallableOp<T, X> action)
            throws X {
        return ScopedValue.where(WORKSPACE, Objects.requireNonNull(workspaceId)).call(action);
    }
}

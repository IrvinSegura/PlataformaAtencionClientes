package com.irvinsegura.supportchat.actions;

public enum ActionRunStatus {
    // Changes something: waits until the visitor confirms or cancels it
    PENDING_CONFIRMATION,
    // Confirmed; the webhook is being called
    RUNNING,
    // The business's system answered 2xx
    SUCCEEDED,
    // It answered with an error, or could not be reached
    FAILED,
    // The visitor said no
    CANCELLED,
    // Nobody confirmed it in time
    EXPIRED,
    // Never sent: unknown action or arguments that do not match its parameters
    REJECTED
}

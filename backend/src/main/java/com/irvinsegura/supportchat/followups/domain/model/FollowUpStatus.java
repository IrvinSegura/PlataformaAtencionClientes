package com.irvinsegura.supportchat.followups.domain.model;

public enum FollowUpStatus {
    // Waiting for its moment
    SCHEDULED,
    // Written to the customer as a reply of the member who scheduled it
    SENT,
    // Could not reach the customer, e.g. 24 hours after their last message on Messenger
    FAILED,
    // A member called it off before its moment
    CANCELLED
}

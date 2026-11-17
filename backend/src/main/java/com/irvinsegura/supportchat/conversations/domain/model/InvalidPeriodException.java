package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidPeriodException extends DomainException {

    public InvalidPeriodException(int maxDays) {
        super("INVALID_PERIOD", "The period must start before it ends and span at most %d days".formatted(maxDays));
    }
}

package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class QuestionLimitReachedException extends DomainException {

    public QuestionLimitReachedException() {
        super("QUESTION_LIMIT_REACHED", "This conversation reached its question limit; start a new one");
    }
}

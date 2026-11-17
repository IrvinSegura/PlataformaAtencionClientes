package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class DifficultQuestionNotFoundException extends NotFoundException {

    public DifficultQuestionNotFoundException() {
        super("DIFFICULT_QUESTION_NOT_FOUND", "Difficult question not found");
    }
}

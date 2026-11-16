package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class UnreadableDocumentException extends DomainException {

    public UnreadableDocumentException(String message) {
        super("UNREADABLE_DOCUMENT", message);
    }
}

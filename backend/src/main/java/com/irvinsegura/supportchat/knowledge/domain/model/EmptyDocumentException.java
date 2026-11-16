package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class EmptyDocumentException extends DomainException {

    public EmptyDocumentException() {
        super("EMPTY_DOCUMENT", "The document has no text to learn from");
    }
}

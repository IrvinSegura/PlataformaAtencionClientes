package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class DocumentTooLargeException extends DomainException {

    public DocumentTooLargeException() {
        super("DOCUMENT_TOO_LARGE", "The document is larger than allowed");
    }
}

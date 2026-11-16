package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

public class DocumentBusyException extends ConflictException {

    public DocumentBusyException() {
        super("DOCUMENT_BUSY", "The document is being processed; try again when it finishes");
    }
}

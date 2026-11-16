package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class DocumentNotFoundException extends NotFoundException {

    public DocumentNotFoundException() {
        super("DOCUMENT_NOT_FOUND", "Document not found");
    }
}

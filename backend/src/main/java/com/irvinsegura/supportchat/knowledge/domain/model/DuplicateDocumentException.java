package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

public class DuplicateDocumentException extends ConflictException {

    public DuplicateDocumentException() {
        super("DUPLICATE_DOCUMENT", "This file was already uploaded");
    }
}

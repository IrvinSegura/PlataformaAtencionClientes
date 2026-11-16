package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidDocumentException extends DomainException {

    public InvalidDocumentException(String message) {
        super("INVALID_DOCUMENT", message);
    }
}

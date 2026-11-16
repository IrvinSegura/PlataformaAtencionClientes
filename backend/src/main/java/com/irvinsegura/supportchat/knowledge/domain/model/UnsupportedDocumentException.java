package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class UnsupportedDocumentException extends DomainException {

    public UnsupportedDocumentException() {
        super("UNSUPPORTED_DOCUMENT", "Upload a PDF, Word (.docx, .doc), Excel (.xlsx, .xls), Markdown (.md), HTML or plain text (.txt) file");
    }
}

package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

public class DuplicateWebPageException extends ConflictException {

    public DuplicateWebPageException() {
        super("DUPLICATE_WEB_PAGE", "This page is already in the knowledge base; process it again to refresh it");
    }
}

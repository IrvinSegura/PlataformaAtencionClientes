package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class KnowledgeGapNotFoundException extends NotFoundException {

    public KnowledgeGapNotFoundException() {
        super("KNOWLEDGE_GAP_NOT_FOUND", "Knowledge gap not found");
    }
}

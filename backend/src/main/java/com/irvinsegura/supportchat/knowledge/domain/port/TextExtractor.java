package com.irvinsegura.supportchat.knowledge.domain.port;

import com.irvinsegura.supportchat.knowledge.domain.model.DocumentType;

public interface TextExtractor {

    /**
     * @throws com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException when the file is
     *                                                                                        damaged, encrypted or
     *                                                                                        not valid text
     */
    String extract(DocumentType type, byte[] content);
}

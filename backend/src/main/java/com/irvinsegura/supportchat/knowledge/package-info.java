/**
 * Knowledge base of each workspace: uploaded documents are extracted, split into chunks and embedded in the
 * background, then found again with hybrid search (vector similarity fused with keyword matching). Other modules use
 * the {@link com.irvinsegura.supportchat.knowledge.KnowledgeSearch} API.
 */
@ApplicationModule(displayName = "Knowledge")
package com.irvinsegura.supportchat.knowledge;

import org.springframework.modulith.ApplicationModule;

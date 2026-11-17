/**
 * Knowledge base of each workspace: uploaded documents are extracted, split into chunks and embedded in the
 * background, then found again with hybrid search (vector similarity fused with keyword matching). Other modules use
 * the {@link com.irvinsegura.supportchat.knowledge.KnowledgeSearch} API. Questions the assistant could not answer
 * arrive as {@link com.irvinsegura.supportchat.knowledge.QuestionUnanswered} and are grouped into knowledge gaps, so
 * the team sees what the knowledge base is missing and can teach the answer.
 */
@ApplicationModule(displayName = "Knowledge")
package com.irvinsegura.supportchat.knowledge;

import org.springframework.modulith.ApplicationModule;

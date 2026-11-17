package com.irvinsegura.supportchat.conversations.domain.model;

import java.util.UUID;

/**
 * A source an answer relies on, referenced in its text as {@code [number]}.
 *
 * @param excerpt the start of the cited passage, enough for the widget to show where the fact comes from
 */
public record Citation(int number, UUID documentId, String documentTitle, UUID chunkId, String excerpt) {
}

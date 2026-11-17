package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

/**
 * The attention settings cannot be saved as given.
 */
public class InvalidAttentionSettingsException extends DomainException {

    public InvalidAttentionSettingsException(String message) {
        super("INVALID_ATTENTION_SETTINGS", message);
    }
}

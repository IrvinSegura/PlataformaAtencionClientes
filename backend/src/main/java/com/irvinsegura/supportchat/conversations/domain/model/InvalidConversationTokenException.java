package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnauthenticatedException;

public class InvalidConversationTokenException extends UnauthenticatedException {

    public InvalidConversationTokenException() {
        super("INVALID_CONVERSATION_TOKEN", "This conversation has expired; start a new one");
    }
}

package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class MessageNotFoundException extends NotFoundException {

    public MessageNotFoundException() {
        super("MESSAGE_NOT_FOUND", "There is no such answer in this conversation");
    }
}

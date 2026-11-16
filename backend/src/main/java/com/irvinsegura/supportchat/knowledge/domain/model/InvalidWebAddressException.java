package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidWebAddressException extends DomainException {

    public InvalidWebAddressException(String message) {
        super("INVALID_WEB_ADDRESS", message);
    }
}

package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnauthenticatedException;

public class InvalidCredentialsException extends UnauthenticatedException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}

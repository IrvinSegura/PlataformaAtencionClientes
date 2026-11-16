package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnauthenticatedException;

public class InvalidRefreshTokenException extends UnauthenticatedException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "The session has expired; sign in again");
    }
}

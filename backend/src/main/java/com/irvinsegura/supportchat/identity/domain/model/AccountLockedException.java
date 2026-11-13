package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.UnauthenticatedException;

public class AccountLockedException extends UnauthenticatedException {

    public AccountLockedException() {
        super("ACCOUNT_LOCKED", "Too many failed attempts; try again later");
    }
}

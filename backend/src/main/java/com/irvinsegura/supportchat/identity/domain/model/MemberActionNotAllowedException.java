package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.shared.domain.ForbiddenException;

public class MemberActionNotAllowedException extends ForbiddenException {

    public MemberActionNotAllowedException(String message) {
        super("MEMBER_ACTION_NOT_ALLOWED", message);
    }
}

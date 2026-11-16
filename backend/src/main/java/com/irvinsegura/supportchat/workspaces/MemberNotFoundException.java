package com.irvinsegura.supportchat.workspaces;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class MemberNotFoundException extends NotFoundException {

    public MemberNotFoundException() {
        super("MEMBER_NOT_FOUND", "This user is not a member of the workspace");
    }
}

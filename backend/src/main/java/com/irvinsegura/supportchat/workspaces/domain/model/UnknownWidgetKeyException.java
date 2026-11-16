package com.irvinsegura.supportchat.workspaces.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class UnknownWidgetKeyException extends NotFoundException {

    public UnknownWidgetKeyException() {
        super("WIDGET_NOT_FOUND", "No widget exists with this key");
    }
}

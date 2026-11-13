package com.irvinsegura.supportchat.shared.web;

/**
 * Headers of the public widget API, shared by the module that serves it and the one that sets up its CORS rules.
 */
public final class WidgetHeaders {

    /**
     * Secret the visitor's browser received when the conversation started; it proves the visitor owns it.
     */
    public static final String CONVERSATION_TOKEN = "X-Conversation-Token";

    private WidgetHeaders() {
    }
}

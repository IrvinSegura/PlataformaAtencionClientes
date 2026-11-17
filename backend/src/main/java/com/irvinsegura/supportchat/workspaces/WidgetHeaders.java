package com.irvinsegura.supportchat.workspaces;

/**
 * Headers of the public widget API: the conversations module reads them and this module allows them in its CORS rules.
 */
public final class WidgetHeaders {

    /**
     * Secret the visitor's browser received when the conversation started; it proves the visitor owns it.
     */
    public static final String CONVERSATION_TOKEN = "X-Conversation-Token";

    private WidgetHeaders() {
    }
}

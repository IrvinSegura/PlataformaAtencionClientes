package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.i18n.LocalizedTexts;

/**
 * What channel messages say besides the reply itself, in one of the languages the platform speaks, from
 * {@code texts/channels/<code>.properties}; Spanish when the language has no file. The widget words the same notices
 * in its own texts.
 */
final class ChannelTexts {

    private static final LocalizedTexts TEXTS = LocalizedTexts.load("channels");

    private final Language language;

    private ChannelTexts(Language language) {
        this.language = language;
    }

    static ChannelTexts in(Language language) {
        return new ChannelTexts(TEXTS.languages().contains(language) ? language : Language.ES);
    }

    /**
     * Introduces the documents an answer cites.
     */
    String sources() {
        return text("sources");
    }

    // Button labels; WhatsApp, Messenger and Instagram allow 20 characters
    String confirm() {
        return text("confirm");
    }

    String cancel() {
        return text("cancel");
    }

    /**
     * Stands for the team member when their name is unknown.
     */
    String someone() {
        return text("someone");
    }

    String handoffRequested() {
        return text("handoff-requested");
    }

    String handoffCancelled() {
        return text("handoff-cancelled");
    }

    String joined(String agent) {
        return TEXTS.text(language, "joined", agent);
    }

    /**
     * A team member left and the assistant goes on.
     */
    String left(String agent) {
        return TEXTS.text(language, "left", agent);
    }

    String closed() {
        return text("closed");
    }

    /**
     * Email has no buttons: how to confirm or cancel an action by replying.
     */
    String replyToConfirm() {
        return text("reply-to-confirm");
    }

    /**
     * The subject of a reply to an email that had none.
     */
    String subject() {
        return text("subject");
    }

    private String text(String key) {
        return TEXTS.text(language, key);
    }
}

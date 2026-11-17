package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Collects the text of Tika's XHTML events and stops the parse once it has more than it may keep.
 */
abstract class LimitedTextHandler extends DefaultHandler {

    protected final StringBuilder text = new StringBuilder();
    private final int maxChars;
    private boolean limitReached;

    LimitedTextHandler(int maxChars) {
        this.maxChars = maxChars;
    }

    final String text() {
        return text.toString();
    }

    final boolean limitReached() {
        return limitReached;
    }

    /**
     * Throwing is the only way to stop a SAX parse early.
     */
    protected final void checkLimit() throws SAXException {
        if (text.length() > maxChars) {
            limitReached = true;
            throw new SAXException("Text limit of " + maxChars + " characters reached");
        }
    }

    /**
     * Ends the current paragraph with a blank line, unless one is already there.
     */
    protected final void paragraph() {
        int length = text.length();
        boolean atBreak = length >= 2 && text.charAt(length - 1) == '\n' && text.charAt(length - 2) == '\n';
        if (length > 0 && !atBreak) {
            text.append("\n\n");
        }
    }
}

package com.irvinsegura.supportchat.shared.domain;

/**
 * Text from outside, made storable: PostgreSQL refuses the NUL character in text columns, so one stray NUL in a
 * customer's message, a mail or a page would make the whole write fail.
 */
public final class StoredText {

    private StoredText() {
    }

    /**
     * @return the text without NUL characters, or null for null
     */
    public static String of(String text) {
        return text == null || text.indexOf('\0') < 0 ? text : text.replace("\0", "");
    }
}

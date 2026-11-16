package com.irvinsegura.supportchat.workspaces.domain.model;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Public identifier the widget sends to say which workspace it belongs to. It is visible in every page that embeds
 * the widget, so it grants nothing by itself: browsers are held to the workspace's allowed origins and every caller
 * to rate limits.
 */
public record WidgetKey(String value) {

    private static final String PREFIX = "wk_";
    private static final int RANDOM_LENGTH = 32;
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Pattern FORMAT = Pattern.compile("wk_[0-9A-Za-z]{" + RANDOM_LENGTH + "}");
    private static final SecureRandom RANDOM = new SecureRandom();

    public WidgetKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Malformed widget key");
        }
    }

    public static WidgetKey generate() {
        StringBuilder key = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            key.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return new WidgetKey(key.toString());
    }

    /**
     * Cheap format check before touching the database, so random paths cost nothing.
     */
    public static boolean isWellFormed(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}

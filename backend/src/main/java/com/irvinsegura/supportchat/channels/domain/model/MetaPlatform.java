package com.irvinsegura.supportchat.channels.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The Meta apps that reach a business through a Facebook Page and the Messenger Platform. Both share the webhook
 * format and the Send API; they differ in what a delivery is called and how long a message may be.
 */
public enum MetaPlatform {

    // Text up to 2000 characters
    MESSENGER("page", 2000, false),
    // Text up to 1000 bytes of UTF-8
    INSTAGRAM("instagram", 1000, true);

    private final String webhookObject;
    private final int maxText;
    private final boolean measuredInBytes;

    MetaPlatform(String webhookObject, int maxText, boolean measuredInBytes) {
        this.webhookObject = webhookObject;
        this.maxText = maxText;
        this.measuredInBytes = measuredInBytes;
    }

    /**
     * The {@code object} field of this platform's webhook deliveries.
     */
    public String webhookObject() {
        return webhookObject;
    }

    /**
     * The longest text one message can carry, in this platform's unit.
     */
    public int maxText() {
        return maxText;
    }

    /**
     * How much of a message's length a character takes: UTF-16 units, or UTF-8 bytes on Instagram.
     */
    public int size(int codePoint) {
        return measuredInBytes ? Character.toString(codePoint).getBytes(StandardCharsets.UTF_8).length
                : Character.charCount(codePoint);
    }

    /**
     * The name in URLs: {@code messenger} or {@code instagram}.
     */
    public String pathName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MetaPlatform> ofPathName(String name) {
        return Arrays.stream(values()).filter(platform -> platform.pathName().equals(name)).findFirst();
    }
}

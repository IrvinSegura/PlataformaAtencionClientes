package com.irvinsegura.supportchat.channels.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Meta signs each webhook delivery with the app secret: the {@code X-Hub-Signature-256} header holds
 * {@code sha256=} followed by the hex HMAC-SHA256 of the exact bytes of the body.
 */
public final class WebhookSignature {

    private static final String PREFIX = "sha256=";

    private WebhookSignature() {
    }

    public static String sign(String appSecret, byte[] body) {
        return PREFIX + HexFormat.of().formatHex(hmac(appSecret, body));
    }

    /**
     * Compares in constant time, so the response time gives nothing away about a guessed signature.
     */
    public static boolean matches(String appSecret, byte[] body, String header) {
        if (header == null || !header.startsWith(PREFIX)) {
            return false;
        }
        byte[] given;
        try {
            given = HexFormat.of().parseHex(header.substring(PREFIX.length()).strip());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(appSecret, body), given);
    }

    private static byte[] hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}

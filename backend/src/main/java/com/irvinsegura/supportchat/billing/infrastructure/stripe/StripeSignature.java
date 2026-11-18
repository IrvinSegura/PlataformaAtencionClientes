package com.irvinsegura.supportchat.billing.infrastructure.stripe;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stripe signs each webhook delivery: the {@code Stripe-Signature} header holds {@code t=<unix seconds>} and one or
 * more {@code v1=<hex HMAC-SHA256 of "t.body">} (several while a secret is being rolled)
 * (https://docs.stripe.com/webhooks#verify-manually).
 */
final class StripeSignature {

    private StripeSignature() {
    }

    static String sign(String secret, long timestamp, byte[] body) {
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac(secret, timestamp, body));
    }

    /**
     * Compares in constant time and refuses deliveries signed longer ago than the tolerance, so a captured one
     * cannot be replayed later.
     */
    static boolean matches(String secret, byte[] body, String header, Instant now, Duration tolerance) {
        if (header == null || secret == null || secret.isBlank()) {
            return false;
        }
        Long timestamp = null;
        for (String part : header.split(",")) {
            String[] pair = part.strip().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        if (timestamp == null
                || Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(tolerance) > 0) {
            return false;
        }
        byte[] expected = hmac(secret, timestamp, body);
        for (String part : header.split(",")) {
            String[] pair = part.strip().split("=", 2);
            if (pair.length == 2 && pair[0].equals("v1")) {
                try {
                    if (MessageDigest.isEqual(expected, HexFormat.of().parseHex(pair[1]))) {
                        return true;
                    }
                } catch (IllegalArgumentException e) {
                    // Not hex: not a match
                }
            }
        }
        return false;
    }

    private static byte[] hmac(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}

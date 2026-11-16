package com.irvinsegura.supportchat.identity.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Length over composition rules, as NIST SP 800-63B recommends: long passphrases are both stronger and easier to
 * remember than short passwords with forced symbols.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    // BCrypt ignores everything after 72 bytes; longer input would silently weaken the password
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String password, Email email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new WeakPasswordException("Use at least %d characters".formatted(MIN_LENGTH));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new WeakPasswordException("Use at most %d bytes".formatted(MAX_BYTES));
        }
        if (password.codePoints().distinct().count() < 4) {
            throw new WeakPasswordException("Use more varied characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String localPart = email.value().substring(0, email.value().indexOf('@'));
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            throw new WeakPasswordException("The password must not contain your email");
        }
    }
}

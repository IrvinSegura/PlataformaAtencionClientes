package com.irvinsegura.supportchat.identity.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A normalized email address. Lowercasing the whole address (not only the domain) is technically stricter than the
 * standard, but every major provider treats the local part case-insensitively, and it stops "Ana@x.com" and
 * "ana@x.com" from becoming two accounts.
 */
public record Email(String value) {

    public static final int MAX_LENGTH = 254;

    // Deliberately simple: the real check is whether mail arrives, not a regular expression
    private static final Pattern FORMAT = Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}");

    public static Email of(String raw) {
        String normalized = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new InvalidEmailException();
        }
        return new Email(normalized);
    }

    @Override
    public String toString() {
        return value;
    }
}

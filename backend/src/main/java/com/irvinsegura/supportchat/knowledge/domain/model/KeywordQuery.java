package com.irvinsegura.supportchat.knowledge.domain.model;

import com.irvinsegura.supportchat.shared.domain.SearchTerms;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns a visitor's question into a PostgreSQL full-text query that matches passages containing <em>any</em> of its
 * meaningful words. Questions are phrased naturally ("¿cuánto tarda el envío a Monterrey?"), so requiring every word
 * would rarely match; ranking then favours passages with more of them.
 */
public final class KeywordQuery {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MAX_TERMS = 16;

    private KeywordQuery() {
    }

    /**
     * Terms joined with {@code |}, or empty when the question has no word worth searching for. Terms only contain
     * letters and digits, so the result is always a valid {@code to_tsquery} expression.
     */
    public static Optional<String> from(String question) {
        if (question == null) {
            return Optional.empty();
        }
        String terms = Arrays.stream(NON_WORD.split(question.toLowerCase(Locale.ROOT)))
                .filter(SearchTerms::isMeaningful)
                .distinct()
                .limit(MAX_TERMS)
                .collect(Collectors.joining(" | "));
        return terms.isEmpty() ? Optional.empty() : Optional.of(terms);
    }
}

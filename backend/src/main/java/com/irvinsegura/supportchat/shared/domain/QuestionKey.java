package com.irvinsegura.supportchat.shared.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Groups questions that differ only in case, accents, punctuation or spacing, which is how visitors usually vary the
 * same question ("¿Cuánto cuesta el envío?" and "cuanto cuesta el envio").
 */
public final class QuestionKey {

    public static final int MAX_LENGTH = 300;

    private QuestionKey() {
    }

    public static String of(String question) {
        String withoutAccents = Normalizer.normalize(question, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String key = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
        return key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH) : key;
    }
}

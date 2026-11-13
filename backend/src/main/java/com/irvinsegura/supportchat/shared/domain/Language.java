package com.irvinsegura.supportchat.shared.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The languages customers are answered in. The model answers in whatever language the customer writes; these are the
 * ones the platform's own texts (fixed replies, notices, the widget) are written in.
 */
public enum Language {
    ES("es"),
    EN("en"),
    PT("pt"),
    FR("fr");

    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Map<Language, Set<String>> COMMON_WORDS = new EnumMap<>(Map.of(
            ES, Set.of("el", "la", "los", "las", "que", "qué", "de", "del", "en", "y", "es", "por", "para", "con",
                    "cómo", "como", "cuánto", "cuanto", "cuánta", "cuál", "cuando", "cuándo", "dónde", "donde", "hay",
                    "tienen", "tiene", "puedo", "quiero", "mi", "mis", "un", "una", "hola", "gracias", "envío", "envio",
                    "pedido", "está", "son", "se", "lo", "al", "hacen", "venden", "precio", "también", "pero", "muy",
                    "usted", "ustedes", "necesito", "aceptan", "envían", "tarda",
                    // Spanish only, common in complaints: "no" and "para" alone would read as Portuguese
                    "no", "esto", "eso", "este", "nada", "ya", "hasta", "tengo", "estoy", "sirve", "servicio",
                    "mucho", "llega", "nadie", "todavía", "porque", "ayuda", "hablar", "persona", "pésimo"),
            EN, Set.of("the", "is", "are", "do", "does", "can", "how", "what", "when", "where", "which", "why", "my",
                    "your", "i", "you", "to", "of", "and", "for", "with", "have", "has", "it", "this", "that", "hello",
                    "hi", "thanks", "thank", "please", "order", "shipping", "much", "long", "there", "an", "be", "will",
                    "would", "not", "ship", "accept", "need", "want", "take", "get", "price", "cost"),
            PT, Set.of("o", "os", "as", "que", "de", "do", "da", "dos", "das", "em", "no", "na", "é", "você", "vocês",
                    "como", "quanto", "quando", "onde", "posso", "quero", "meu", "minha", "um", "uma", "olá", "ola",
                    "obrigado", "obrigada", "pedido", "envio", "não", "sim", "tem", "têm", "fazem", "para", "com",
                    "por", "também", "mas", "muito", "frete", "está", "são", "aceitam", "preciso", "entrega", "demora"),
            FR, Set.of("le", "la", "les", "des", "du", "de", "est", "et", "en", "un", "une", "pour", "avec", "que",
                    "qui", "quoi", "comment", "combien", "quand", "où", "je", "vous", "nous", "mon", "ma", "mes",
                    "bonjour", "merci", "commande", "livraison", "pas", "ne", "il", "elle", "sont", "ce", "cette",
                    "avez", "peux", "puis", "voulez", "au", "aux", "acceptez", "livrez", "prix", "besoin")));

    private final String code;

    Language(String code) {
        this.code = code;
    }

    /**
     * The ISO 639-1 code, e.g. "es".
     */
    public String code() {
        return code;
    }

    public static Optional<Language> ofCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String primary = code.strip().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        for (Language language : values()) {
            if (language.code.equals(primary)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * The first of the browser's preferred languages that the platform speaks.
     *
     * @param acceptLanguage an Accept-Language header such as {@code "en-US,en;q=0.9,es;q=0.8"}; may be null
     */
    public static Optional<Language> preferred(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Optional.empty();
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        for (Locale.LanguageRange range : ranges) {
            Optional<Language> language = ofCode(range.getRange());
            if (language.isPresent()) {
                return language;
            }
        }
        return Optional.empty();
    }

    /**
     * Guesses the language of a customer's message from its common words and letters. Short or ambiguous messages
     * ("ok", "123") give no answer, so the conversation keeps the language it had.
     */
    public static Optional<Language> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Map<Language, Integer> scores = new EnumMap<>(Language.class);
        Matcher words = WORD.matcher(lower);
        while (words.find()) {
            String word = words.group();
            COMMON_WORDS.forEach((language, common) -> {
                if (common.contains(word)) {
                    scores.merge(language, 1, Integer::sum);
                }
            });
        }
        // Letters and marks only one of these languages uses
        if (lower.indexOf('¿') >= 0 || lower.indexOf('¡') >= 0 || lower.indexOf('ñ') >= 0) {
            scores.merge(ES, 2, Integer::sum);
        }
        if (lower.indexOf('ã') >= 0 || lower.indexOf('õ') >= 0) {
            scores.merge(PT, 2, Integer::sum);
        }
        if (lower.indexOf('è') >= 0 || lower.indexOf('ù') >= 0 || lower.indexOf('œ') >= 0 || lower.contains("ç")
                && !lower.contains("ção") && !lower.contains("ções")) {
            scores.merge(FR, 1, Integer::sum);
        }
        Language best = null;
        int bestScore = 0;
        int runnerUp = 0;
        for (Map.Entry<Language, Integer> score : scores.entrySet()) {
            if (score.getValue() > bestScore) {
                runnerUp = bestScore;
                best = score.getKey();
                bestScore = score.getValue();
            } else if (score.getValue() > runnerUp) {
                runnerUp = score.getValue();
            }
        }
        return bestScore > runnerUp ? Optional.of(best) : Optional.empty();
    }
}

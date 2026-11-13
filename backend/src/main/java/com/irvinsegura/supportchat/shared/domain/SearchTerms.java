package com.irvinsegura.supportchat.shared.domain;

import java.util.Set;

/**
 * Which words carry meaning for search. Articles, prepositions, question words and greetings appear in almost every
 * passage and question, so matching on them makes unrelated text look related.
 */
public final class SearchTerms {

    // Frequent Spanish and English words, with and without accents, since some callers fold them away
    private static final Set<String> STOP_WORDS = Set.of(
            "que", "qué", "los", "las", "del", "por", "para", "con", "una", "uno", "unos", "unas", "como", "cómo",
            "cual", "cuál", "cuales", "cuáles", "cuando", "cuándo", "donde", "dónde", "quien", "quién", "quienes",
            "quiénes", "cuanto", "cuánto", "cuanta", "cuánta", "cuantos", "cuántos", "cuantas", "cuántas", "esta",
            "este", "esto", "estos", "estas", "eso", "son", "hay", "pero", "más", "mas", "muy", "sus", "ser", "fue",
            "han", "tiene", "tienen", "puedo", "puede", "quiero", "hola", "gracias", "favor", "también", "tambien",
            "sobre", "entre", "sin", "desde", "hasta",
            "the", "and", "for", "are", "with", "what", "how", "can", "you", "your", "this", "that", "from",
            "have", "does", "when", "where", "which", "who", "about", "there", "their", "will", "would", "please");

    private SearchTerms() {
    }

    /**
     * Whether a lowercase word is worth searching for. Numbers matter even when short (sizes, hours, plan numbers);
     * words need three letters.
     */
    public static boolean isMeaningful(String term) {
        boolean hasDigit = term.chars().anyMatch(Character::isDigit);
        return (hasDigit ? !term.isEmpty() : term.length() >= 3) && !STOP_WORDS.contains(term);
    }
}

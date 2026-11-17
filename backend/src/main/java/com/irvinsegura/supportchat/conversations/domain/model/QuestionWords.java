package com.irvinsegura.supportchat.conversations.domain.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The words that carry what a question is about, to tell two wordings of the same question apart from two different
 * questions without an embedding call: "Si compro 3 y uno llega roto, ¿me devuelven todo?" and "si compro dos y uno
 * viene roto me devuelven todo" share almost all of theirs. Lower case, without accents, without the short words every
 * question has ("el", "the", "que") and with a plural "s" dropped, so "pedidos" and "pedido" are one word.
 */
public final class QuestionWords {

    // A question with fewer words than this says too little to be matched with another
    public static final int MIN_WORDS = 2;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    // Set.copyOf keeps a word two languages share, where Set.of would refuse it
    private static final Set<String> STOP_WORDS = Set.copyOf(java.util.List.of(
            // Spanish
            "que", "los", "las", "del", "con", "por", "para", "una", "uno", "unos", "unas", "como", "cual", "cuales",
            "donde", "cuando", "esta", "este", "esto", "eso", "ese", "esa", "hay", "son", "tienen", "tiene", "puedo",
            "pueden", "mis", "sus", "tus", "nos", "les", "mas", "muy", "pero", "porque", "hola", "favor", "gracias",
            "quiero", "saber", "seria", "usted", "ustedes",
            // English
            "the", "and", "for", "you", "your", "are", "can", "does", "did", "what", "which", "how", "where", "when",
            "this", "that", "have", "has", "with", "from", "please", "thanks", "there", "would", "could",
            // Portuguese
            "voce", "voces", "uma", "dos", "das", "pelo", "pela", "isso", "esse", "essa", "tem", "posso", "obrigado",
            "qual", "quais", "onde", "quando", "mais",
            // French
            "des", "une", "est", "pour", "avec", "vous", "votre", "vos", "quel", "quelle", "quels", "quelles",
            "comment", "pourquoi", "merci", "puis", "dans", "sur", "pas"));

    private QuestionWords() {
    }

    /**
     * The question's words that say what it is about, sorted so equal sets read the same.
     */
    public static Set<String> of(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }
        String folded = MARKS.matcher(Normalizer.normalize(question.toLowerCase(java.util.Locale.ROOT),
                Normalizer.Form.NFD)).replaceAll("");
        return Arrays.stream(NON_WORD.split(folded))
                .filter(word -> word.length() > 2 || word.chars().allMatch(Character::isDigit) && !word.isEmpty())
                .filter(word -> !STOP_WORDS.contains(word))
                .map(QuestionWords::singular)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The words both questions have over the words either has (Jaccard): 1 for the same words, and well below 1 when
     * one word that matters differs, as in "envío a Monterrey" and "envío a Guadalajara".
     */
    public static double similarity(Set<String> a, Set<String> b) {
        if (a.size() < MIN_WORDS || b.size() < MIN_WORDS) {
            return 0;
        }
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / (a.size() + b.size() - shared);
    }

    private static String singular(String word) {
        // "devoluciones" and "colores" drop "es"; "paquetes" only its "s"
        if (word.length() > 4 && word.endsWith("es") && "nrldjy".indexOf(word.charAt(word.length() - 3)) >= 0) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}

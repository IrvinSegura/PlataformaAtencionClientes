package com.irvinsegura.supportchat.conversations.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tells whether a customer's message reads as upset, without calling the language model, in Spanish, English,
 * Portuguese and French. It adds up signals: insults and strong complaints weigh two, softer ones (repeating
 * themselves, waiting too long, shouting in capitals, "!!!", angry emoji) weigh one, and two points are needed.
 * <p>
 * It errs on the side of silence: a single "otra vez" or a capitalised word is not enough, because a false alarm
 * takes the customer away from an assistant that was helping them and wakes the owner for nothing.
 */
public final class FrustrationDetector {

    public static final int THRESHOLD = 2;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern REPEATED_PUNCTUATION = Pattern.compile("[!?¡¿]{3,}");
    // Shouting needs enough letters to tell it from an acronym such as "DHL"
    private static final int MIN_SHOUTED_LETTERS = 10;
    private static final double SHOUTED_SHARE = 0.7;

    // Folded: lower case, without accents, words separated by single spaces
    private static final Set<String> STRONG = Set.of(
            // Spanish
            "no sirve", "no sirves", "no sirven", "no me sirve", "no sirve para nada", "no me entiendes",
            "no entiendes nada", "no entiendes", "pesimo", "pesima", "horrible", "terrible", "estafa", "estafadores",
            "fraude", "ladrones", "rateros", "harto", "harta", "hartos", "estoy harto", "estoy harta",
            "hasta la madre", "inutil", "inutiles", "ridiculo", "ridicula", "una verguenza", "que verguenza",
            "es una burla", "me quiero quejar", "pongo una queja", "voy a demandar", "profeco", "denuncia",
            "mierda", "carajo", "chingada", "chingado", "pinche", "puta", "puto", "joder", "cabron", "maldita",
            "maldito", "basura", "molesto", "molesta", "muy molesto", "muy molesta", "enojado", "enojada",
            "furioso", "furiosa", "indignado", "indignada", "decepcionado", "decepcionada",
            // English
            "useless", "worst", "awful", "scam", "fraud", "ridiculous", "fed up", "sick of", "not helpful",
            "you dont understand", "you don t understand", "this is a joke", "unacceptable", "disgusting", "angry",
            "furious", "pissed", "fuck", "fucking", "shit", "crap", "wtf", "damn",
            // Portuguese
            "nao serve", "nao funciona nada", "pessimo", "pessima", "lixo", "absurdo", "palhacada",
            "estou farto", "estou farta", "merda", "porra", "irritado", "irritada",
            // French
            "nul", "nulle", "arnaque", "escroquerie", "inadmissible", "honteux", "j en ai marre", "marre",
            "merde", "putain", "furieux", "furieuse", "enerve", "enervee");

    private static final Set<String> SOFT = Set.of(
            // Spanish
            "ya te dije", "ya lo dije", "te lo dije", "otra vez", "de nuevo", "por enesima vez", "cuantas veces",
            "llevo horas", "llevo dias", "llevo semanas", "nadie me responde", "nadie responde", "nadie contesta",
            "sigo esperando", "todavia nada", "aun nada", "no me ayudas", "no ayudas", "no me resuelves",
            "no resuelves", "no funciona", "nunca llega", "no ha llegado", "no llega", "en serio", "por favor ya",
            "que mal", "mal servicio", "mala atencion", "respondan", "contesten",
            // English
            "i already told you", "already told you", "again", "still waiting", "for hours", "for days",
            "nobody answers", "no one answers", "not working", "doesnt work", "doesn t work", "seriously",
            "bad service", "come on",
            // Portuguese
            "de novo", "ja disse", "ninguem responde", "ainda esperando", "nao funciona", "serio",
            // French
            "encore", "deja dit", "personne ne repond", "toujours rien", "ca ne marche pas", "serieux");

    private static final List<String> ANGRY_EMOJI = List.of("😡", "🤬", "😠", "👎", "💩", "😤", "🙄");

    private FrustrationDetector() {
    }

    public static boolean isFrustrated(String text) {
        return assess(text).score() >= THRESHOLD;
    }

    /**
     * The signals found in a message and their total weight.
     */
    public static Assessment assess(String text) {
        if (text == null || text.isBlank()) {
            return new Assessment(0, List.of());
        }
        List<String> signals = new ArrayList<>();
        int score = 0;
        String folded = " " + String.join(" ", NON_WORD.split(fold(text).strip())) + " ";
        for (String phrase : STRONG) {
            if (folded.contains(" " + phrase + " ")) {
                signals.add(phrase);
                score += 2;
            }
        }
        for (String phrase : SOFT) {
            if (folded.contains(" " + phrase + " ")) {
                signals.add(phrase);
                score += 1;
            }
        }
        if (REPEATED_PUNCTUATION.matcher(text).find()) {
            signals.add("!!!");
            score += 1;
        }
        if (shouted(text)) {
            signals.add("CAPS");
            score += 1;
        }
        if (ANGRY_EMOJI.stream().anyMatch(text::contains)) {
            signals.add("emoji");
            score += 1;
        }
        return new Assessment(score, List.copyOf(signals));
    }

    private static boolean shouted(String text) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        return letters >= MIN_SHOUTED_LETTERS && upper >= letters * SHOUTED_SHARE;
    }

    private static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
    }

    /**
     * @param signals the phrases and marks that were found, for logs and tests
     */
    public record Assessment(int score, List<String> signals) {

        public boolean frustrated() {
            return score >= THRESHOLD;
        }
    }
}

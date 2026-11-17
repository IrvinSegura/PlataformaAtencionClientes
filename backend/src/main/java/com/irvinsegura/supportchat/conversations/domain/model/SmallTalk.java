package com.irvinsegura.supportchat.conversations.domain.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recognises messages that are only courtesy, such as "Hola", "¡Muchas gracias!" or "ok, bye", so the assistant can
 * answer them with a fixed reply instead of searching the knowledge base and paying for a model call that could only
 * say it has no information. A message counts only when every word in it is courtesy: "Hola, ¿cuánto cuesta el
 * envío?" is a question and goes through the normal flow.
 * <p>
 * Words that also mean "yes" ("sí", "claro", "dale") are left out on purpose: they usually answer something the
 * assistant asked, and a canned reply would ignore it.
 */
public final class SmallTalk {

    static final int MAX_WORDS = 8;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    // Thumbs up, folded hands, hearts and smiling faces
    private static final Pattern COURTESY_EMOJI = Pattern.compile(
            "[\\x{1F44D}\\x{1F44C}\\x{1F64F}\\x{1F64C}\\x{1F44B}\\x{2764}\\x{1F49B}\\x{1F499}\\x{1F49A}\\x{1F49C}"
                    + "\\x{1F642}\\x{1F600}-\\x{1F60D}\\x{1F917}\\x{263A}\\x{1F970}\\x{1F973}]");

    private static final Set<String> THANKS = Set.of(
            "gracias", "graciass", "agradezco", "agradecido", "agradecida", "thanks", "thank", "thx", "ty",
            "obrigado", "obrigada", "valeu", "merci");
    private static final Set<String> GOODBYE = Set.of(
            "adios", "bye", "chao", "chau", "hasta", "luego", "pronto", "vemos", "goodbye", "later", "tchau", "revoir");
    private static final Set<String> GREETING = Set.of(
            "hola", "holaa", "holi", "holis", "buenas", "buenos", "buen", "dia", "dias", "tardes", "noches", "saludos",
            "hi", "hello", "hey", "morning", "afternoon", "evening", "oi", "ola", "bom", "boa", "tarde", "noite",
            "bonjour", "bonsoir", "salut", "coucou",
            // The other half of "¿qué tal?", "¿cómo estás?", "how are you?", "tudo bem?" and "ça va?"
            "tal", "estas", "esta", "estan", "are", "tudo", "ca");
    private static final Set<String> ACKNOWLEDGEMENT = Set.of(
            "ok", "okay", "oki", "okey", "vale", "perfecto", "excelente", "genial", "listo", "entendido", "great",
            "perfect", "cool", "nice", "perfeito", "otimo", "parfait", "super", "bien", "good", "bem");
    // Words that only fill a courtesy phrase: "muchas gracias", "que tal", "how are you", "tudo bem", "ça va"
    private static final Set<String> FILLER = Set.of(
            "muchas", "muchisimas", "mil", "muy", "amable", "eres", "es", "usted", "te", "lo", "se", "le", "que",
            "como", "todo", "a", "y", "por", "su", "tu", "la", "el", "ayuda", "atencion", "informacion",
            "igualmente", "nos", "manana", "you", "so", "much", "very", "how", "see", "it", "got",
            "and", "for", "your", "help", "muito", "ate", "logo", "e", "voce", "pela", "beaucoup",
            "au", "bonne", "journee", "soiree", "va", "et");

    private SmallTalk() {
    }

    /**
     * What kind of courtesy the message is, or empty when it says or asks anything else.
     */
    public static Optional<Kind> of(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String folded = MARKS.matcher(Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        List<String> words = Arrays.stream(NON_WORD.split(folded)).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) {
            // Only symbols: a 👍 or 🙏 is thanks, a lone "?" is not courtesy
            return COURTESY_EMOJI.matcher(message).find() ? Optional.of(Kind.THANKS) : Optional.empty();
        }
        if (words.size() > MAX_WORDS || !words.stream().allMatch(SmallTalk::isCourtesy)) {
            return Optional.empty();
        }
        // Filler words alone ("que", "muy") say nothing on their own
        if (words.stream().allMatch(FILLER::contains)) {
            return Optional.empty();
        }
        // "Hola, gracias" thanks and "ok, bye" says goodbye: the strongest intent wins
        if (words.stream().anyMatch(THANKS::contains)) {
            return Optional.of(Kind.THANKS);
        }
        if (words.stream().anyMatch(GOODBYE::contains)) {
            return Optional.of(Kind.GOODBYE);
        }
        if (words.stream().anyMatch(GREETING::contains)) {
            return Optional.of(Kind.GREETING);
        }
        // "¿ok?" or "bien?" asks something: whether it worked, whether someone is there
        return message.contains("?") ? Optional.empty() : Optional.of(Kind.ACKNOWLEDGEMENT);
    }

    private static boolean isCourtesy(String word) {
        return THANKS.contains(word) || GOODBYE.contains(word) || GREETING.contains(word)
                || ACKNOWLEDGEMENT.contains(word) || FILLER.contains(word);
    }

    public enum Kind {
        GREETING,
        THANKS,
        GOODBYE,
        ACKNOWLEDGEMENT
    }
}

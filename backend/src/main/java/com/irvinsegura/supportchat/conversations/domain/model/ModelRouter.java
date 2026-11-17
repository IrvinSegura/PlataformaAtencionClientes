package com.irvinsegura.supportchat.conversations.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chooses the model for a question before any model is called, with rules cheap enough to run on every question
 * and simple enough to explain on the panel. Most support questions ("¿cuánto tarda el envío?") are direct lookups
 * a small model answers as well as a large one for a fraction of the price; the larger model is kept for questions
 * that ask to compare, explain or reason, for several questions at once, and for answers that must combine several
 * documents.
 * <p>
 * The router only decides where to start: if the cheap model says it cannot answer although relevant sources were
 * found, the answer service asks the capable model once more ({@link #ESCALATED}).
 */
public final class ModelRouter implements ModelSelection {

    public static final String DISABLED = "routing-disabled";
    public static final String LONG_QUESTION = "long-question";
    public static final String SEVERAL_QUESTIONS = "several-questions";
    public static final String REASONING = "reasoning";
    public static final String SEVERAL_DOCUMENTS = "several-documents";
    public static final String SIMPLE = "simple";
    public static final String ESCALATED = "escalated-no-answer";
    // Not routed at all: the answer the model already wrote to the same question was given again
    public static final String REUSED = "reused";
    // A case the customer imagines ("si compro 3 y uno llega roto, ¿me devuelven todo?"): rules must be applied to it
    public static final String HYPOTHETICAL = "hypothetical";
    // Weighs one thing against another ("¿por qué mi plan cuesta más que el de mi amigo?")
    public static final String COMPARISON = "comparison";
    // Customers found the fast model's answers to this question lacking before
    public static final String LEARNED = "learned-difficult";
    // The customer just said, or showed by asking again, that the last answer did not help
    public static final String UNSATISFIED = "customer-unsatisfied";

    static final int LONG_QUESTION_CHARS = 280;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    // A condition before the question: "Si compro 3 y uno llega roto, ¿…?", "If I order two, …", "Se eu cancelar, …".
    // Only at the start, where "se" cannot be the Spanish pronoun, and with a comma, so "Si, ¿tienen talla 30?" is not one
    private static final Pattern CONDITION_FIRST = Pattern.compile(
            "^[¿¡\\s]*(si|if|se|en caso de que|in case|caso|au cas o[uù])\\s+[^,?¿]{3,},\\s*\\S.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    // A condition inside the question: "¿qué hago si…?", "what if…", "e se…", "et si…"
    private static final List<String> CONDITION_CUES = List.of(
            " que hago si ", " que puedo hacer si ", " que pasa si ", " y si ", " what if ", " what do i do if ",
            " what happens if ", " o que acontece se ", " o que faco se ", " e se ", " que se passe t il si ",
            " que faire si ", " et si ");
    // "más/menos … que", "more … than", "mais … que", "plus … que", with up to two words between; not before a number,
    // so "more than 10 kg" stays a lookup
    private static final Pattern COMPARATIVE = Pattern.compile(
            " (mas|menos|mejor|peor|more|less|better|worse|cheaper|mais|melhor|pior|plus|moins|meilleur)"
                    + "( [a-z]+){0,2} (que|than|de lo que|do que) (?!\\d)");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    // Matched against the folded question with spaces around it, so " vs " does not match inside a word. Spanish,
    // English, Portuguese and French; a stem such as " compar" covers every language that shares it
    private static final List<String> REASONING_CUES = List.of(
            " compar", " vs ", " versus ", " paso a paso", " step by step", " passo a passo", " etape par etape",
            " calcul", " explica", " explain", " expliqu", " recomend", " recomiend", " recommend", " recommand",
            " conseill",
            // Spanish
            " diferencia", " conviene", " mejor opcion", " que es mejor ", " cual es mejor ", " en que caso",
            " ventajas", " desventajas", " pros y contras", " vale la pena",
            // English
            " difference", " which is better ", " pros and cons", " advantages", " disadvantages", " worth it",
            " how do i choose ",
            // Portuguese
            " diferenca", " qual e melhor ", " vantagens", " desvantagens", " vale a pena",
            // French
            " lequel ", " laquelle ", " vaut mieux ", " avantages", " inconvenients");
    // "Why" asks to explain, except when the customer asks about their own case ("¿por qué no ha llegado mi
    // pedido?"): that is a lookup of an order or a policy, which the cheap model answers as well
    private static final List<String> WHY_CUES = List.of(" por que ", " why ", " pourquoi ");
    private static final Set<String> OWN_CASE = Set.of(
            "me", "mi", "mis", "yo", "tengo", "puedo", "i", "my", "im", "ive", "meu", "minha", "meus", "minhas", "eu",
            "mon", "ma", "mes", "je", "j", "moi");

    private final boolean enabled;
    private final Thresholds thresholds;

    public ModelRouter(boolean enabled) {
        this(enabled, Thresholds.DEFAULTS);
    }

    public ModelRouter(boolean enabled, Thresholds thresholds) {
        this.enabled = enabled;
        this.thresholds = Objects.requireNonNull(thresholds);
    }

    @Override
    public Routing select(String question, List<Source> sources) {
        return route(question, sources);
    }

    @Override
    public boolean mayEscalate() {
        return true;
    }

    public Routing route(String question, List<Source> sources) {
        if (!enabled) {
            return new Routing(ModelTier.COMPLEX, DISABLED);
        }
        String text = question == null ? "" : question.strip();
        if (text.length() > LONG_QUESTION_CHARS) {
            return new Routing(ModelTier.COMPLEX, LONG_QUESTION);
        }
        if (text.chars().filter(c -> c == '?').count() >= 2) {
            return new Routing(ModelTier.COMPLEX, SEVERAL_QUESTIONS);
        }
        String folded = " " + fold(text) + " ";
        if (CONDITION_FIRST.matcher(text).matches() || CONDITION_CUES.stream().anyMatch(folded::contains)) {
            return new Routing(ModelTier.COMPLEX, HYPOTHETICAL);
        }
        // Before "why": "¿por qué mi plan cuesta más que el de mi amigo?" asks to compare, not to look up an order
        if (COMPARATIVE.matcher(folded).find()) {
            return new Routing(ModelTier.COMPLEX, COMPARISON);
        }
        if (asksToReason(folded)) {
            return new Routing(ModelTier.COMPLEX, REASONING);
        }
        if (distinctDocuments(sources) >= thresholds.severalDocuments()) {
            return new Routing(ModelTier.COMPLEX, SEVERAL_DOCUMENTS);
        }
        return new Routing(ModelTier.SIMPLE, SIMPLE);
    }

    /**
     * Where a question goes when what happened before says the fast model is not enough for it.
     */
    public static Routing learned(boolean customerUnsatisfied) {
        return new Routing(ModelTier.COMPLEX, customerUnsatisfied ? UNSATISFIED : LEARNED);
    }

    private static boolean asksToReason(String folded) {
        if (REASONING_CUES.stream().anyMatch(folded::contains)) {
            return true;
        }
        return WHY_CUES.stream().anyMatch(folded::contains)
                && Arrays.stream(folded.strip().split(" ")).noneMatch(OWN_CASE::contains);
    }

    /**
     * How many documents the answer must really draw on: those whose best passage is about as relevant as the best
     * one overall, counting once the documents that only repeat another's text.
     */
    int distinctDocuments(List<Source> sources) {
        Map<UUID, Source> bestByDocument = new LinkedHashMap<>();
        sources.stream()
                // A passage found only by its words is weaker evidence than the ones found by meaning
                .filter(source -> source.similarity() != null)
                .sorted(Comparator.comparing(Source::similarity).reversed())
                .forEach(source -> bestByDocument.putIfAbsent(source.documentId(), source));
        if (bestByDocument.isEmpty()) {
            return 0;
        }
        double best = bestByDocument.values().iterator().next().similarity();
        // Relative to how far the best passage is above the floor of relevance, so the rule means the same with any
        // embedding model, whose similarities live on scales of their own
        double margin = thresholds.closeness() * Math.max(0, best - thresholds.minSimilarity());
        List<Set<String>> kept = new ArrayList<>();
        for (Source source : bestByDocument.values()) {
            if (source.similarity() < best - margin) {
                break;
            }
            Set<String> words = words(source.content());
            if (kept.stream().noneMatch(other -> overlap(words, other) >= thresholds.sameTextOverlap())) {
                kept.add(words);
            }
        }
        return kept.size();
    }

    private static Set<String> words(String content) {
        return Arrays.stream(fold(Objects.requireNonNullElse(content, "")).split(" "))
                .filter(word -> word.length() > 2)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // Shared words over the words of the shorter passage, so a short passage repeated inside a longer one is the same
    private static double overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = smaller == a ? b : a;
        long shared = smaller.stream().filter(larger::contains).count();
        return (double) shared / smaller.size();
    }

    private static String fold(String text) {
        String withoutAccents = MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        return NON_WORD.matcher(withoutAccents).replaceAll(" ").strip();
    }

    /**
     * @param reason one of the constants of {@link ModelRouter}, recorded with the answer
     */
    public record Routing(ModelTier tier, String reason) {
    }

    /**
     * When an answer must combine several documents.
     *
     * @param severalDocuments documents it must draw on to count as several
     * @param closeness        share of the best passage's lead over {@code minSimilarity} another document may be
     *                         behind and still count; 0.25 counts the documents in the top quarter of that range
     * @param sameTextOverlap  share of shared words from which two passages say the same thing
     * @param minSimilarity    the similarity a passage needs to be used at all, as the source selection sets it
     */
    public record Thresholds(int severalDocuments, double closeness, double sameTextOverlap, double minSimilarity) {

        public static final Thresholds DEFAULTS = new Thresholds(3, 0.25, 0.6, 0.35);

        public Thresholds {
            if (severalDocuments < 2 || closeness < 0 || closeness > 1 || sameTextOverlap <= 0 || sameTextOverlap > 1) {
                throw new IllegalArgumentException("Invalid routing thresholds");
            }
        }
    }
}

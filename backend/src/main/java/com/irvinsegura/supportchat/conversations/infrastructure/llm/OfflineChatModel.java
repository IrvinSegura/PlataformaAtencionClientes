package com.irvinsegura.supportchat.conversations.infrastructure.llm;

import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ChatTurn;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolField;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolResult;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolSpec;
import com.irvinsegura.supportchat.conversations.domain.model.GroundedPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.Source;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.domain.SearchTerms;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Answers without any external service by quoting the best source, word by word, as a real model would stream.
 * Makes the whole platform usable for development, tests and demos without an API key, and its answers are
 * predictable. It does not understand the question: retrieval does almost all the work.
 * <p>
 * Like a real model told to use only its sources, it declines when the best source does not cover the question:
 * fewer than half of the question's meaningful words, or words sharing their stem, appear in it. That keeps a
 * passage that merely shares one word ("escribe un poema" and "escribe a soporte") from becoming an answer.
 * <p>
 * It also uses actions, with the same crude matching: an action whose name and description share at least two words
 * with the question (one, for one-word questions) is called when every required value can be found in the question:
 * text through the parameter's pattern (an order number), numbers as the first number. Both tiers behave the same.
 * What it says after an action is worded in the language the question was written in (Spanish when it can't tell).
 */
class OfflineChatModel implements ChatModel {

    static final String MODEL = "offline-extractive";

    private static final int MAX_ANSWER_CHARS = 400;
    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=\\s)");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final String QUESTION_START = "<question>";
    private static final String QUESTION_END = "</question>";
    private static final String PENDING_PREFIX = "PENDING_CONFIRMATION";
    private static final double MIN_COVERAGE = 0.5;
    private static final int MIN_TOOL_MATCHES = 2;
    // Two words share a stem when they agree on at least four letters and differ at most in the last two of the
    // shorter one: "aceptan" and "aceptamos", "envio" and "envios"
    private static final int MIN_STEM = 4;
    private static final int MAX_SUFFIX = 2;


    @Override
    public String modelName(ModelTier tier) {
        return MODEL;
    }

    @Override
    public Completion stream(ChatPrompt prompt, ModelTier tier, Consumer<String> onText) {
        ChatTurn last = prompt.turns().getLast();
        String question = question(prompt);
        if (last.toolResults().isEmpty()) {
            Optional<ToolCall> call = toolCall(prompt.tools(), question);
            if (call.isPresent()) {
                return new Completion(MODEL, inputTokens(prompt), 1, List.of(call.get()), null);
            }
        }
        Language language = Language.detect(question).orElse(Language.ES);
        String answer = last.toolResults().isEmpty() ? answerFromSources(prompt.sources(), question)
                : answerFromResults(last.toolResults(), ActionReplies.in(language));
        for (String piece : WORD_BOUNDARY.split(answer)) {
            onText.accept(piece);
        }
        return new Completion(MODEL, inputTokens(prompt), estimateTokens(answer));
    }

    private static String answerFromSources(List<Source> sources, String question) {
        return sources.isEmpty() || !covers(sources.getFirst(), question)
                ? GroundedPrompt.NO_ANSWER_MARKER
                : excerpt(sources.getFirst());
    }

    private static String answerFromResults(List<ToolResult> results, ActionReplies replies) {
        ToolResult result = results.getFirst();
        if (result.content().startsWith(PENDING_PREFIX)) {
            return replies.pending();
        }
        if (result.error()) {
            return replies.failed();
        }
        // The first line introduces the business's answer, which follows
        String content = result.content();
        int lineBreak = content.indexOf('\n');
        String answer = (lineBreak >= 0 ? content.substring(lineBreak + 1) : content).replaceAll("\\s+", " ").strip();
        return replies.resultPrefix() + shorten(answer);
    }

    /**
     * What the model says after calling an action: it waits for confirmation, it failed, or it found something.
     */
    record ActionReplies(String pending, String failed, String resultPrefix) {

        static ActionReplies in(Language language) {
            return switch (language) {
                case ES -> new ActionReplies("Para continuar, confirma la acción con el botón.",
                        "Lo siento, no pude completar la acción en este momento.", "Esto es lo que encontré: ");
                case EN -> new ActionReplies("To continue, confirm the action with the button.",
                        "Sorry, I couldn't complete the action right now.", "Here's what I found: ");
                case PT -> new ActionReplies("Para continuar, confirme a ação com o botão.",
                        "Desculpe, não consegui concluir a ação agora.", "Isto é o que encontrei: ");
                case FR -> new ActionReplies("Pour continuer, confirmez l'action avec le bouton.",
                        "Désolé, je n'ai pas pu terminer l'action pour le moment.", "Voici ce que j'ai trouvé : ");
            };
        }
    }

    static Optional<ToolCall> toolCall(List<ToolSpec> tools, String question) {
        Set<String> asked = terms(question);
        ToolCall best = null;
        long bestMatches = 0;
        for (ToolSpec tool : tools) {
            Set<String> described = terms(tool.name().replace('_', ' ') + " " + tool.description());
            long matches = asked.stream()
                    .filter(term -> described.stream().anyMatch(word -> sameStem(term, word)))
                    .count();
            if (matches < Math.min(MIN_TOOL_MATCHES, asked.size()) || matches == 0 || matches <= bestMatches) {
                continue;
            }
            Optional<Map<String, Object>> input = extract(tool.fields(), question);
            if (input.isPresent()) {
                best = new ToolCall("offline-" + tool.name(), tool.name(), input.get());
                bestMatches = matches;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Map<String, Object>> extract(List<ToolField> fields, String question) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (ToolField field : fields) {
            Optional<Object> value = find(field, question);
            if (value.isPresent()) {
                input.put(field.name(), value.get());
            } else if (field.required()) {
                return Optional.empty();
            }
        }
        return Optional.of(input);
    }

    private static Optional<Object> find(ToolField field, String question) {
        if (field.pattern() != null) {
            try {
                Matcher matcher = Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + field.pattern() + ")(?![\\p{L}\\p{N}])")
                        .matcher(question);
                return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
            } catch (PatternSyntaxException e) {
                return Optional.empty();
            }
        }
        // Free text (an address, a name) cannot be told apart from the rest of the question without understanding it
        Matcher number = NUMBER.matcher(question);
        return "number".equals(field.type()) && number.find()
                ? Optional.of(Double.parseDouble(number.group().replace(',', '.')))
                : Optional.empty();
    }

    static boolean covers(Source source, String question) {
        Set<String> asked = terms(question);
        if (asked.isEmpty()) {
            // Nothing to compare: retrieval already judged the source relevant
            return true;
        }
        Set<String> available = terms(source.content());
        long found = asked.stream()
                .filter(term -> available.stream().anyMatch(word -> sameStem(term, word)))
                .count();
        return found >= asked.size() * MIN_COVERAGE;
    }

    private static boolean sameStem(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        int limit = Math.min(left.length(), right.length());
        int common = 0;
        while (common < limit && left.charAt(common) == right.charAt(common)) {
            common++;
        }
        return common >= MIN_STEM && common >= limit - MAX_SUFFIX;
    }

    private static Set<String> terms(String text) {
        String folded = MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        Set<String> terms = new LinkedHashSet<>();
        for (String word : NON_WORD.split(folded)) {
            if (SearchTerms.isMeaningful(word)) {
                terms.add(word);
            }
        }
        return terms;
    }

    // The visitor's last question ends with the question between tags, after the sources
    private static String question(ChatPrompt prompt) {
        String turn = prompt.lastVisitorText();
        int start = turn.lastIndexOf(QUESTION_START);
        int end = turn.lastIndexOf(QUESTION_END);
        return start >= 0 && end > start ? turn.substring(start + QUESTION_START.length(), end).strip() : turn;
    }

    private static String excerpt(Source source) {
        return shorten(source.content().replaceAll("\\s+", " ").strip()) + " [" + source.number() + "]";
    }

    private static String shorten(String content) {
        if (content.length() <= MAX_ANSWER_CHARS) {
            return content;
        }
        int cut = content.lastIndexOf(' ', MAX_ANSWER_CHARS);
        return content.substring(0, cut > 0 ? cut : MAX_ANSWER_CHARS) + "…";
    }

    private static int inputTokens(ChatPrompt prompt) {
        return estimateTokens(prompt.system()) + prompt.turns().stream()
                .mapToInt(turn -> estimateTokens(turn.text()) + turn.toolResults().stream()
                        .mapToInt(result -> estimateTokens(result.content()))
                        .sum())
                .sum();
    }

    // Roughly four characters per token, close enough for usage reports of a model that costs nothing
    private static int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}

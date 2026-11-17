package com.irvinsegura.supportchat.conversations.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything sent to the language model for one answer.
 *
 * @param system  rules the model must follow
 * @param turns   alternating visitor and assistant turns, starting and ending with the visitor
 * @param sources the evidence included in the last question, kept separately for models that work with it directly
 * @param tools   actions the model may ask to run; empty when it may only answer
 */
public record ChatPrompt(String system, List<ChatTurn> turns, List<Source> sources, List<ToolSpec> tools) {

    public ChatPrompt {
        turns = List.copyOf(turns);
        sources = List.copyOf(sources);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (turns.isEmpty() || turns.getFirst().role() != ChatTurn.Role.VISITOR
                || turns.getLast().role() != ChatTurn.Role.VISITOR) {
            throw new IllegalArgumentException("A prompt starts and ends with a visitor turn");
        }
    }

    public ChatPrompt(String system, List<ChatTurn> turns, List<Source> sources) {
        this(system, turns, sources, List.of());
    }

    /**
     * The visitor's latest question, ignoring turns that only carry tool results.
     */
    public String lastVisitorText() {
        for (int i = turns.size() - 1; i >= 0; i--) {
            ChatTurn turn = turns.get(i);
            if (turn.role() == ChatTurn.Role.VISITOR && !turn.text().isEmpty()) {
                return turn.text();
            }
        }
        return "";
    }

    /**
     * One turn of the conversation.
     *
     * @param toolCalls   assistant turns: the actions the model asked for
     * @param toolResults visitor turns: what those actions returned, sent back to the model
     * @param replay      assistant turns: the model's own encoding of the turn (e.g. reasoning blocks some models need
     *                    back unchanged), or null to rebuild it from text and tool calls
     */
    public record ChatTurn(Role role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults,
            String replay) {

        public ChatTurn {
            Objects.requireNonNull(role);
            text = text == null ? "" : text;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        }

        public ChatTurn(Role role, String text) {
            this(role, text, List.of(), List.of(), null);
        }

        public enum Role {
            VISITOR,
            ASSISTANT
        }
    }

    /**
     * The model asks to run an action.
     *
     * @param id    the model's id for the call, which its result must quote
     * @param input the arguments as the model wrote them, unvalidated
     */
    public record ToolCall(String id, String name, Map<String, Object> input) {

        public ToolCall {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            // Keeps the model's order and allows null values, which mean "left out"
            input = input == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(input));
        }
    }

    /**
     * @param error whether the action failed or was refused, so the model does not take the content as a result
     */
    public record ToolResult(String toolCallId, String content, boolean error) {
    }

    /**
     * An action offered to the model.
     */
    public record ToolSpec(String name, String description, List<ToolField> fields) {

        public ToolSpec {
            fields = List.copyOf(fields);
        }
    }

    /**
     * @param type    {@code string}, {@code number} or {@code boolean}
     * @param pattern a regular expression text values must match, or null
     */
    public record ToolField(String name, String type, String description, boolean required, String pattern) {
    }
}

package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.actions.ActionParameter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks what the language model wants to send against the action's parameters before anything leaves the platform.
 * The model's arguments are untrusted: a visitor may have talked it into odd values, so only declared parameters of
 * the declared type pass, text is bounded and must match its pattern.
 */
public final class ActionArguments {

    public static final int MAX_TEXT_LENGTH = 300;

    private ActionArguments() {
    }

    /**
     * @return the arguments in the order of the parameters, with numbers as {@link BigDecimal}
     * @throws InvalidActionArgumentsException describing every problem, worded for the model to correct itself
     */
    public static Map<String, Object> validate(List<ActionParameter> parameters, Map<String, Object> arguments) {
        List<String> problems = new ArrayList<>();
        for (String name : arguments.keySet()) {
            if (parameters.stream().noneMatch(parameter -> parameter.name().equals(name))) {
                problems.add("unknown parameter " + name);
            }
        }
        Map<String, Object> valid = new LinkedHashMap<>();
        for (ActionParameter parameter : parameters) {
            Object value = arguments.get(parameter.name());
            if (value instanceof String text && text.isBlank()) {
                value = null;
            }
            if (value == null) {
                if (parameter.required()) {
                    problems.add(parameter.name() + " is required; ask the customer for it");
                }
                continue;
            }
            Object converted = convert(parameter, value, problems);
            if (converted != null) {
                valid.put(parameter.name(), converted);
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidActionArgumentsException(String.join("; ", problems));
        }
        return Collections.unmodifiableMap(valid);
    }

    private static Object convert(ActionParameter parameter, Object value, List<String> problems) {
        String name = parameter.name();
        return switch (parameter.type()) {
            case STRING -> {
                if (!(value instanceof String) && !(value instanceof Number)) {
                    problems.add(name + " must be text");
                    yield null;
                }
                String text = value.toString().strip();
                if (text.length() > MAX_TEXT_LENGTH) {
                    problems.add(name + " is too long");
                    yield null;
                }
                if (parameter.pattern() != null && !Pattern.compile(parameter.pattern()).matcher(text).matches()) {
                    problems.add(name + " does not have the expected format");
                    yield null;
                }
                yield text;
            }
            case NUMBER -> {
                try {
                    BigDecimal number = value instanceof Number n ? new BigDecimal(n.toString())
                            : new BigDecimal(value.toString().strip());
                    yield number;
                } catch (NumberFormatException e) {
                    problems.add(name + " must be a number");
                    yield null;
                }
            }
            case BOOLEAN -> {
                if (value instanceof Boolean b) {
                    yield b;
                }
                problems.add(name + " must be true or false");
                yield null;
            }
        };
    }
}

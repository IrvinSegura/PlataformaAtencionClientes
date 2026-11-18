package com.irvinsegura.supportchat.actions;

import org.jspecify.annotations.Nullable;

/**
 * One value the model must provide to run an action.
 *
 * @param pattern a regular expression a text value must match entirely, e.g. {@code \d{5,10}} for an order number;
 *                null for none
 */
public record ActionParameter(String name, ParameterType type, String description, boolean required,
        @Nullable String pattern) {
}

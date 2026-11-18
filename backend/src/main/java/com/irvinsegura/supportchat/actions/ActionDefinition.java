package com.irvinsegura.supportchat.actions;

import java.util.List;

/**
 * An action as the language model sees it.
 *
 * @param name        the tool name, e.g. {@code order_status}
 * @param title       what people read, e.g. "Consultar un pedido"
 * @param description when the model should use it
 */
public record ActionDefinition(String name, String title, String description, List<ActionParameter> parameters,
        boolean requiresConfirmation) {

    public ActionDefinition {
        parameters = List.copyOf(parameters);
    }
}

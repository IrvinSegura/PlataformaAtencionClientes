package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.ParameterType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The actions a connected integration gives the assistant. They are offered to the model like a workspace's own
 * actions and recorded the same way; their names are reserved, so no webhook action can take them.
 * <p>
 * Orders are only shown or changed for the person who placed them: the customer must give the email address of the
 * order, unless they write by email, where the channel already verified it.
 */
public enum IntegrationAction {

    SHOPIFY_ORDER_STATUS(IntegrationProvider.SHOPIFY, "shopify_order_status", "Consultar un pedido",
            "Looks up an order of the store by its number: payment, shipping status and tracking. Use it when the "
                    + "customer asks where their order is or what happened to it.",
            false, List.of(Parameters.ORDER_NUMBER, Parameters.EMAIL)),
    SHOPIFY_CANCEL_ORDER(IntegrationProvider.SHOPIFY, "shopify_cancel_order", "Cancelar un pedido",
            "Cancels an order of the store that has not shipped yet, refunding the payment and restocking the "
                    + "items. Use it only when the customer clearly asks to cancel their order.",
            true, List.of(Parameters.ORDER_NUMBER, Parameters.EMAIL)),
    ZENDESK_CREATE_TICKET(IntegrationProvider.ZENDESK, "zendesk_create_ticket", "Crear un ticket de soporte",
            "Opens a ticket for the support team in Zendesk, who will answer the customer by email. Use it when the "
                    + "customer needs something only the team can do and agrees to be contacted later.",
            true, List.of(
                    new ActionParameter("subject", ParameterType.STRING,
                            "A short summary of the problem, in the customer's language", true, ".{3,150}"),
                    new ActionParameter("description", ParameterType.STRING,
                            "What the customer needs, with every detail they gave (order numbers, dates)", true,
                            "(?s).{10,2000}"),
                    Parameters.EMAIL,
                    new ActionParameter("name", ParameterType.STRING, "The customer's name, if they gave it", false,
                            ".{1,100}")));

    private final IntegrationProvider provider;
    private final String actionName;
    private final String title;
    private final String description;
    private final boolean requiresConfirmation;
    private final List<ActionParameter> parameters;

    IntegrationAction(IntegrationProvider provider, String actionName, String title, String description,
            boolean requiresConfirmation, List<ActionParameter> parameters) {
        this.provider = provider;
        this.actionName = actionName;
        this.title = title;
        this.description = description;
        this.requiresConfirmation = requiresConfirmation;
        this.parameters = parameters;
    }

    public IntegrationProvider provider() {
        return provider;
    }

    /**
     * The tool name the model sees.
     */
    public String actionName() {
        return actionName;
    }

    public String title() {
        return title;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    public List<ActionParameter> parameters() {
        return parameters;
    }

    public ActionDefinition definition() {
        return new ActionDefinition(actionName, title, description, parameters, requiresConfirmation);
    }

    public static Optional<IntegrationAction> ofName(String name) {
        return Arrays.stream(values()).filter(action -> action.actionName.equals(name)).findFirst();
    }

    public static List<IntegrationAction> of(IntegrationProvider provider) {
        return Arrays.stream(values()).filter(action -> action.provider == provider).toList();
    }

    /**
     * Whether a webhook action's name would clash with an integration's, now or as integrations grow.
     */
    public static boolean isReserved(String name) {
        return Arrays.stream(IntegrationProvider.values())
                .anyMatch(provider -> name.startsWith(provider.pathName() + "_"));
    }

    private static final class Parameters {

        static final ActionParameter ORDER_NUMBER = new ActionParameter("order_number", ParameterType.STRING,
                "The order number the customer sees, e.g. 1001 or #1001", true, "#?\\d{1,12}");
        static final ActionParameter EMAIL = new ActionParameter("email", ParameterType.STRING,
                "The customer's email address; ask for it unless they are writing by email", false,
                "[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{1,63}");
    }
}

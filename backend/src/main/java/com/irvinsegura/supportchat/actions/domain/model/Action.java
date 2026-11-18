package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.ParameterType;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * An action a workspace lets its assistant take: a webhook of the business, described well enough for the language
 * model to know when to call it and with what. Validated as a whole, so a stored action is always one the model API
 * accepts as a tool.
 */
public final class Action {

    public static final int MAX_PER_WORKSPACE = 20;
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_URL_LENGTH = 500;
    public static final int MAX_PARAMETERS = 10;
    public static final int MAX_PARAMETER_DESCRIPTION_LENGTH = 200;
    public static final int MAX_PATTERN_LENGTH = 200;

    // Tool names the model API accepts, kept lowercase so two actions never differ only in case
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    private final UUID id;
    private final UUID workspaceId;
    private String name;
    private String title;
    private String description;
    private String endpointUrl;
    private List<ActionParameter> parameters;
    private boolean requiresConfirmation;
    private boolean enabled;
    private final Instant createdAt;
    private Instant updatedAt;

    private Action(UUID id, UUID workspaceId, String name, String title, String description, String endpointUrl,
            List<ActionParameter> parameters, boolean requiresConfirmation, boolean enabled, Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.name = name;
        this.title = title;
        this.description = description;
        this.endpointUrl = endpointUrl;
        this.parameters = List.copyOf(parameters);
        this.requiresConfirmation = requiresConfirmation;
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Action create(UUID id, UUID workspaceId, Details details, Instant now) {
        Details valid = details.validated();
        return new Action(id, workspaceId, valid.name(), valid.title(), valid.description(), valid.endpointUrl(),
                valid.parameters(), valid.requiresConfirmation(), valid.enabled(), now, now);
    }

    public static Action restore(UUID id, UUID workspaceId, String name, String title, String description,
            String endpointUrl, List<ActionParameter> parameters, boolean requiresConfirmation, boolean enabled,
            Instant createdAt, Instant updatedAt) {
        return new Action(id, workspaceId, name, title, description, endpointUrl, parameters, requiresConfirmation,
                enabled, createdAt, updatedAt);
    }

    public void change(Details details, Instant now) {
        Details valid = details.validated();
        name = valid.name();
        title = valid.title();
        description = valid.description();
        endpointUrl = valid.endpointUrl();
        parameters = valid.parameters();
        requiresConfirmation = valid.requiresConfirmation();
        enabled = valid.enabled();
        updatedAt = now;
    }

    public ActionDefinition definition() {
        return new ActionDefinition(name, title, description, parameters, requiresConfirmation);
    }

    public UUID id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String endpointUrl() {
        return endpointUrl;
    }

    public List<ActionParameter> parameters() {
        return parameters;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    public boolean enabled() {
        return enabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * What an operator can set.
     */
    public record Details(String name, String title, String description, String endpointUrl,
            List<ActionParameter> parameters, boolean requiresConfirmation, boolean enabled) {

        Details validated() {
            String validName = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
            if (!NAME.matcher(validName).matches()) {
                throw new InvalidActionException("The name must be 2 to 64 lowercase letters, digits or underscores, "
                        + "starting with a letter, e.g. order_status");
            }
            if (IntegrationAction.isReserved(validName)) {
                throw new InvalidActionException("Names starting with shopify_ or zendesk_ belong to the "
                        + "integrations");
            }
            String validTitle = text(title, "title", MAX_TITLE_LENGTH);
            String validDescription = text(description, "description", MAX_DESCRIPTION_LENGTH);
            String url = endpoint(endpointUrl);
            List<ActionParameter> validParameters = parameters(parameters == null ? List.of() : parameters);
            return new Details(validName, validTitle, validDescription, url, validParameters, requiresConfirmation,
                    enabled);
        }

        private static String text(String value, String field, int maxLength) {
            String stripped = value == null ? "" : value.strip();
            if (stripped.isEmpty() || stripped.length() > maxLength) {
                throw new InvalidActionException("The %s must have between 1 and %d characters"
                        .formatted(field, maxLength));
            }
            return stripped;
        }

        // Only the form of the address is checked here; whether it may be called (https, no private network) is
        // decided by the endpoint policy, which depends on the deployment
        private static String endpoint(String value) {
            String url = value == null ? "" : value.strip();
            URI uri;
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                throw new InvalidActionException("The endpoint must be a valid URL");
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (url.length() > MAX_URL_LENGTH || !(scheme.equals("https") || scheme.equals("http"))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new InvalidActionException("The endpoint must be an http(s) URL of at most %d characters, "
                        + "without credentials or fragment".formatted(MAX_URL_LENGTH));
            }
            return url;
        }

        private static List<ActionParameter> parameters(List<ActionParameter> parameters) {
            if (parameters.size() > MAX_PARAMETERS) {
                throw new InvalidActionException("An action has at most %d parameters".formatted(MAX_PARAMETERS));
            }
            Set<String> names = new HashSet<>();
            return parameters.stream().map(parameter -> {
                if (parameter == null) {
                    throw new InvalidActionException("Parameters cannot be empty");
                }
                String parameterName = parameter.name() == null ? ""
                        : parameter.name().strip().toLowerCase(Locale.ROOT);
                if (!NAME.matcher(parameterName).matches() || !names.add(parameterName)) {
                    throw new InvalidActionException("Parameter names must be unique, 2 to 64 lowercase letters, "
                            + "digits or underscores");
                }
                ParameterType type = Objects.requireNonNullElse(parameter.type(), ParameterType.STRING);
                String parameterDescription = text(parameter.description(), "description of " + parameterName,
                        MAX_PARAMETER_DESCRIPTION_LENGTH);
                String pattern = parameter.pattern() == null || parameter.pattern().isBlank() ? null
                        : parameter.pattern().strip();
                if (pattern != null) {
                    if (type != ParameterType.STRING || pattern.length() > MAX_PATTERN_LENGTH) {
                        throw new InvalidActionException("Only text parameters take a pattern, of at most %d "
                                .formatted(MAX_PATTERN_LENGTH) + "characters");
                    }
                    try {
                        Pattern.compile(pattern);
                    } catch (PatternSyntaxException e) {
                        throw new InvalidActionException("The pattern of " + parameterName
                                + " is not a valid regular expression");
                    }
                }
                return new ActionParameter(parameterName, type, parameterDescription, parameter.required(), pattern);
            }).toList();
        }
    }
}

package com.irvinsegura.supportchat.shared.web;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Says in the API description which response fields are always there and which may be null, so the panel's types are
 * checked on both. Every response is a record that Jackson writes whole, nulls included: each field is required, and
 * the ones whose type is {@code @Nullable} (JSpecify) may hold null. The tests fail when any other field is null.
 */
@Component
public class ResponseFields implements OpenApiCustomizer, ModelConverter {

    private static final String BASE_PACKAGE = "com.irvinsegura.supportchat.";

    // The record each schema was made from, noted as springdoc makes them
    private final Map<String, Class<?>> records = new ConcurrentHashMap<>();

    /**
     * Whether a response field may be null: its type is annotated {@code @Nullable}.
     */
    public static boolean nullable(RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class);
    }

    /**
     * The names of the schemas that some response holds, directly or inside another one.
     */
    public static Set<String> responseSchemas(OpenAPI api) {
        Map<String, Schema> schemas = api.getComponents() == null || api.getComponents().getSchemas() == null
                ? Map.of()
                : api.getComponents().getSchemas();
        Deque<Schema<?>> pending = new ArrayDeque<>();
        if (api.getPaths() != null) {
            api.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .filter(operation -> operation.getResponses() != null)
                    .flatMap(operation -> operation.getResponses().values().stream())
                    .filter(response -> response.getContent() != null)
                    .flatMap(response -> response.getContent().values().stream())
                    .map(media -> media.getSchema())
                    .filter(Objects::nonNull)
                    .forEach(pending::add);
        }
        Set<String> reached = new TreeSet<>();
        while (!pending.isEmpty()) {
            Schema<?> schema = pending.pop();
            if (schema.get$ref() != null) {
                String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
                if (reached.add(name) && schemas.containsKey(name)) {
                    pending.add(schemas.get(name));
                }
                continue;
            }
            children(schema).forEach(pending::add);
        }
        return reached;
    }

    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> schema = chain.next().resolve(type, context, chain);
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (schema != null && raw.isRecord() && raw.getName().startsWith(BASE_PACKAGE)) {
            String name = schema.get$ref() != null
                    ? schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1)
                    : schema.getName();
            if (name != null) {
                Class<?> earlier = records.putIfAbsent(name, raw);
                // Two with the same fields, such as one request per controller, share a schema without harm
                if (earlier != null && !shape(earlier).equals(shape(raw))) {
                    throw new IllegalStateException("Two records are called " + name + " in the API description, "
                            + "which cannot tell them apart: rename " + earlier.getName() + " or " + raw.getName());
                }
            }
        }
        return schema;
    }

    private static List<String> shape(Class<?> record) {
        return Stream.of(record.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getAnnotatedType())
                .toList();
    }

    @Override
    public void customise(OpenAPI api) {
        for (String name : responseSchemas(api)) {
            Schema<?> schema = api.getComponents().getSchemas().get(name);
            Class<?> record = records.get(name);
            if (schema != null && schema.getProperties() != null && record != null) {
                describe(schema, record);
            }
        }
    }

    private static void describe(Schema<?> schema, Class<?> record) {
        Map<String, Schema> properties = schema.getProperties();
        List<String> required = new ArrayList<>(schema.getRequired() == null ? List.of() : schema.getRequired());
        for (RecordComponent component : record.getRecordComponents()) {
            Schema<?> property = properties.get(component.getName());
            if (property == null) {
                continue;
            }
            if (!required.contains(component.getName())) {
                required.add(component.getName());
            }
            if (nullable(component)) {
                properties.put(component.getName(), orNull(property));
            }
        }
        schema.setRequired(required);
    }

    // A reference cannot take another type next to it, so it becomes one of the referenced schema or null
    private static Schema<?> orNull(Schema<?> property) {
        if (property.get$ref() != null || property.getTypes() == null) {
            return new JsonSchema().oneOf(List.of(property, new JsonSchema().types(Set.of("null"))));
        }
        property.addType("null");
        if (property.getEnum() != null) {
            List<Object> values = new ArrayList<>(property.getEnum());
            values.add(null);
            ((Schema<Object>) property).setEnum(values);
        }
        return property;
    }

    private static Stream<Schema<?>> children(Schema<?> schema) {
        List<Schema<?>> children = new ArrayList<>();
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(children::add);
        }
        if (schema.getItems() != null) {
            children.add(schema.getItems());
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> values) {
            children.add(values);
        }
        Stream.of(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf(), schema.getPrefixItems())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .forEach(children::add);
        return children.stream();
    }
}

package com.irvinsegura.supportchat.support;

import com.irvinsegura.supportchat.shared.web.ResponseFields;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Makes a response fail when a field the API description promises is never null holds null: the panel's types trust
 * that promise. Fields that may be null say so with {@code @Nullable}. With {@code -Dnull-fields.report=<file>} each
 * such field is written to that file instead, to find them all in one run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class NullFieldGuard {

    private static final Path CONTRACT = Path.of("..", "docs", "openapi.json");
    private static final String REPORT = System.getProperty("null-fields.report", "");

    @Bean
    SimpleModule nullFieldGuardModule() {
        Set<String> responses = responseSchemas();
        SimpleModule module = new SimpleModule("null-field-guard");
        module.setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                    BeanDescription.Supplier description, List<BeanPropertyWriter> writers) {
                Class<?> type = description.getBeanClass();
                if (!type.isRecord() || !responses.contains(type.getSimpleName())
                        || !type.getName().startsWith("com.irvinsegura.supportchat.")) {
                    return writers;
                }
                Map<String, RecordComponent> components = Arrays.stream(type.getRecordComponents())
                        .collect(Collectors.toMap(RecordComponent::getName, Function.identity()));
                return writers.stream()
                        .map(writer -> {
                            RecordComponent component = components.get(writer.getMember().getName());
                            return component == null || ResponseFields.nullable(component)
                                    ? writer
                                    : new NeverNull(writer, type.getSimpleName() + "." + component.getName());
                        })
                        .toList();
            }
        });
        return module;
    }

    private static Set<String> responseSchemas() {
        try {
            OpenAPI api = Json31.mapper().readValue(Files.readString(CONTRACT, StandardCharsets.UTF_8), OpenAPI.class);
            return ResponseFields.responseSchemas(api);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class NeverNull extends BeanPropertyWriter {

        private final String field;

        NeverNull(BeanPropertyWriter base, String field) {
            super(base);
            this.field = field;
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context)
                throws Exception {
            if (get(bean) == null) {
                found(field);
            }
            super.serializeAsProperty(bean, generator, context);
        }
    }

    private static void found(String field) {
        if (REPORT.isEmpty()) {
            throw new IllegalStateException(field + " is null, but the API description says it never is: annotate "
                    + "its type @Nullable (org.jspecify.annotations) if it may be");
        }
        try {
            Files.writeString(Path.of(REPORT), field + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

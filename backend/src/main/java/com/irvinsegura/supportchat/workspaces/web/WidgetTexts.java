package com.irvinsegura.supportchat.workspaces.web;

import com.irvinsegura.supportchat.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * The widget's own texts (buttons, labels, notices) in the languages other than Spanish, which the widget ships with.
 * Read once from {@code widget-texts/<code>.properties}.
 */
@Component
class WidgetTexts {

    static final Language BUILT_IN = Language.ES;

    private final Map<Language, Map<String, String>> texts = new EnumMap<>(Language.class);

    WidgetTexts() {
        for (Language language : Language.values()) {
            if (language != BUILT_IN) {
                texts.put(language, load(language));
            }
        }
    }

    /**
     * @return empty for the language the widget already speaks
     */
    Map<String, String> in(Language language) {
        return texts.getOrDefault(language, Map.of());
    }

    private static Map<String, String> load(Language language) {
        String path = "widget-texts/" + language.code() + ".properties";
        try (InputStream stream = WidgetTexts.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + path);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> loaded = new TreeMap<>();
            properties.stringPropertyNames().forEach(key -> loaded.put(key, properties.getProperty(key)));
            return Collections.unmodifiableMap(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

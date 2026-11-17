package com.irvinsegura.supportchat.shared.i18n;

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
import java.util.Set;
import java.util.TreeMap;

/**
 * Texts the platform words itself (fixed replies, notices, button labels, alerts), in each language it speaks. Every
 * module keeps them the same way: one file per language, {@code texts/<bundle>/<code>.properties} in UTF-8, read once.
 * A {@code {}} in a text takes the next argument.
 */
public final class LocalizedTexts {

    private final String bundle;
    private final Map<Language, Map<String, String>> texts;

    private LocalizedTexts(String bundle, Map<Language, Map<String, String>> texts) {
        this.bundle = bundle;
        this.texts = texts;
    }

    /**
     * Reads every language the bundle has a file for; a language without one is simply not there.
     */
    public static LocalizedTexts load(String bundle) {
        Map<Language, Map<String, String>> texts = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            Map<String, String> loaded = read("texts/" + bundle + "/" + language.code() + ".properties");
            if (loaded != null) {
                texts.put(language, loaded);
            }
        }
        if (texts.isEmpty()) {
            throw new IllegalStateException("No texts found for texts/" + bundle);
        }
        return new LocalizedTexts(bundle, texts);
    }

    public Set<Language> languages() {
        return Collections.unmodifiableSet(texts.keySet());
    }

    /**
     * @return every text of the language, empty when the bundle does not have it
     */
    public Map<String, String> in(Language language) {
        return texts.getOrDefault(language, Map.of());
    }

    /**
     * @throws IllegalStateException when the language or the key is missing, which is a mistake in the files
     */
    public String text(Language language, String key, Object... arguments) {
        String text = in(language).get(key);
        if (text == null) {
            throw new IllegalStateException("Missing text " + key + " in texts/" + bundle + "/" + language.code());
        }
        return fill(text, arguments);
    }

    /**
     * Fails at startup instead of on the first message that needs a missing text.
     */
    public LocalizedTexts requireKeys(Language language, Set<String> keys) {
        for (String key : keys) {
            text(language, key);
        }
        return this;
    }

    static String fill(String text, Object... arguments) {
        if (arguments.length == 0) {
            return text;
        }
        StringBuilder filled = new StringBuilder(text.length() + 32);
        int from = 0;
        for (Object argument : arguments) {
            int at = text.indexOf("{}", from);
            if (at < 0) {
                break;
            }
            filled.append(text, from, at).append(argument);
            from = at + 2;
        }
        return filled.append(text, from, text.length()).toString();
    }

    private static Map<String, String> read(String path) {
        try (InputStream stream = LocalizedTexts.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
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

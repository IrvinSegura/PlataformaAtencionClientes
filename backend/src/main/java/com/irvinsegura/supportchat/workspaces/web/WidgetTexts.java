package com.irvinsegura.supportchat.workspaces.web;

import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.i18n.LocalizedTexts;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The widget's own texts (buttons, labels, notices) in the languages other than Spanish, from
 * {@code texts/widget/<code>.properties}. The widget ships its Spanish texts itself, so the API only sends the others,
 * keyed as the widget names them.
 */
@Component
class WidgetTexts {

    static final Language BUILT_IN = Language.ES;

    private static final Pattern AFTER_HYPHEN = Pattern.compile("-([a-z0-9])");

    private final Map<Language, Map<String, String>> byLanguage = new EnumMap<>(Language.class);

    WidgetTexts() {
        LocalizedTexts texts = LocalizedTexts.load("widget");
        for (Language language : Language.values()) {
            if (language == BUILT_IN) {
                continue;
            }
            if (!texts.languages().contains(language)) {
                throw new IllegalStateException("Missing texts/widget/" + language.code() + ".properties");
            }
            Map<String, String> named = new LinkedHashMap<>();
            texts.in(language).forEach((key, text) -> named.put(widgetKey(key), text));
            byLanguage.put(language, Map.copyOf(named));
        }
    }

    /**
     * @return empty for the language the widget already speaks
     */
    Map<String, String> in(Language language) {
        return byLanguage.getOrDefault(language, Map.of());
    }

    /**
     * The name the widget gives a text: {@code input-label} is {@code inputLabel}, and a notice, which is named after
     * the notice's type, {@code notices.agent-joined} is {@code notices.AGENT_JOINED}.
     */
    static String widgetKey(String key) {
        int dot = key.indexOf('.');
        if (dot >= 0) {
            return key.substring(0, dot + 1) + key.substring(dot + 1).replace('-', '_').toUpperCase(Locale.ROOT);
        }
        return AFTER_HYPHEN.matcher(key).replaceAll(match -> match.group(1).toUpperCase(Locale.ROOT));
    }
}

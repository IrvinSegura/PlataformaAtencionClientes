package com.irvinsegura.supportchat.conversations.domain.model;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds which sources an answer actually cites. Markers pointing at sources that were never offered (the model
 * invented a number) are ignored rather than trusted.
 */
public final class Citations {

    private static final Pattern MARKER = Pattern.compile("\\[(\\d{1,2})]");

    private Citations() {
    }

    public static List<Citation> citedIn(String answer, List<Source> sources) {
        Map<Integer, Source> byNumber = sources.stream()
                .collect(Collectors.toMap(Source::number, Function.identity()));
        TreeSet<Integer> cited = new TreeSet<>();
        Matcher matcher = MARKER.matcher(answer);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (byNumber.containsKey(number)) {
                cited.add(number);
            }
        }
        return cited.stream().map(number -> byNumber.get(number).toCitation()).toList();
    }
}

package com.irvinsegura.supportchat.knowledge.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a document into passages small enough to be quoted precisely and large enough to keep their context.
 * Paragraphs are kept whole when they fit; longer ones are split between sentences, and only a sentence longer than
 * a whole chunk is cut between words. Each chunk starts with the end of the previous one, so a fact spanning a
 * boundary is still found in one piece.
 *
 * @param maxChars     longest chunk, in characters
 * @param overlapChars how much of the previous chunk is repeated at the start of the next
 */
public record TextChunker(int maxChars, int overlapChars) {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");
    // Postgres text cannot hold NUL, and other control characters only confuse tokenizers
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final String SEPARATOR = "\n\n";

    public TextChunker {
        if (maxChars < 100) {
            throw new IllegalArgumentException("maxChars must be at least 100");
        }
        if (overlapChars < 0 || overlapChars > maxChars / 2) {
            throw new IllegalArgumentException("overlapChars must be between 0 and half of maxChars");
        }
    }

    public List<String> split(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> pieces = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BREAK.split(normalized)) {
            String stripped = paragraph.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.length() <= maxChars) {
                pieces.add(stripped);
            } else {
                pieces.addAll(splitLongParagraph(stripped));
            }
        }
        return pack(pieces);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = CONTROL.matcher(text.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
        StringBuilder result = new StringBuilder(cleaned.length());
        for (String line : cleaned.split("\n", -1)) {
            result.append(SPACES.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        return result.toString().replaceAll("\\n{3,}", "\n\n").strip();
    }

    private List<String> pack(List<String> pieces) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (!current.isEmpty() && current.length() + SEPARATOR.length() + piece.length() > maxChars) {
                chunks.add(current.toString());
                String tail = overlapTail(current.toString());
                current.setLength(0);
                // The overlap is a courtesy: it is dropped when it would push the next piece over the limit
                if (!tail.isEmpty() && tail.length() + SEPARATOR.length() + piece.length() <= maxChars) {
                    current.append(tail);
                }
            }
            if (!current.isEmpty()) {
                current.append(SEPARATOR);
            }
            current.append(piece);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_END.split(paragraph)) {
            List<String> sentenceParts = sentence.length() <= maxChars ? List.of(sentence) : cutBetweenWords(sentence);
            for (String part : sentenceParts) {
                if (!current.isEmpty() && current.length() + 1 + part.length() > maxChars) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(part);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private List<String> cutBetweenWords(String sentence) {
        List<String> parts = new ArrayList<>();
        String rest = sentence;
        while (rest.length() > maxChars) {
            int cut = rest.lastIndexOf(' ', maxChars);
            // A single "word" longer than a chunk (e.g. a URL or a base64 blob) is cut where the limit falls
            if (cut <= 0) {
                cut = maxChars;
            }
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    private String overlapTail(String chunk) {
        if (overlapChars == 0 || chunk.length() <= overlapChars) {
            return "";
        }
        String tail = chunk.substring(chunk.length() - overlapChars);
        // Starts at a word boundary, so the overlap never begins mid-word
        int space = tail.indexOf(' ');
        return space < 0 ? "" : tail.substring(space + 1).strip();
    }
}

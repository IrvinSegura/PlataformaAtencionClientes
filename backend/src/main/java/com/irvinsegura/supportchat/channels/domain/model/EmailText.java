package com.irvinsegura.supportchat.channels.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the body of a customer's mail into what they actually wrote: HTML becomes text, and the earlier messages a
 * reply quotes below it, as well as the signature, are cut off.
 */
public final class EmailText {

    // "On Tue, 10 Nov 2026 at 9:00, Ana <ana@example.com> wrote:", as Gmail, Apple Mail and Outlook word it in the
    // languages the platform speaks; clients wrap it over two lines when it is long
    private static final Pattern ATTRIBUTION_START = Pattern.compile("^(on|el|em|le|am)\\s.+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTION_END = Pattern.compile(
            "(wrote|escribió|escreveu|a écrit|schrieb)\\s*:\\s*$", Pattern.CASE_INSENSITIVE);
    // Outlook's header block of the quoted message: "From: ..." followed by "Sent: ..." or "Date: ..."
    private static final Pattern HEADER_FROM = Pattern.compile("^\\*?(from|de|von|da)\\s*:\\*?\\s.+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_DATE = Pattern.compile(
            "^\\*?(sent|date|enviado|fecha|enviada|data|envoyé|gesendet)\\s*:\\*?\\s.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR = Pattern.compile(
            "^(-{2,}\\s*(original message|mensaje original|mensagem original|message d'origine)\\s*-{2,}|_{10,})\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCKS = Pattern.compile("(?i)<\\s*(br|/p|/div|/li|/tr|/h[1-6])\\b[^>]*>");
    private static final Pattern INVISIBLE = Pattern.compile("(?is)<(head|style|script)\\b.*?</\\1\\s*>");
    private static final Pattern QUOTES = Pattern.compile("(?is)<blockquote\\b.*?</blockquote\\s*>");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
    private static final Map<String, String> ENTITIES = Map.of("&nbsp;", " ", "&lt;", "<", "&gt;", ">", "&quot;",
            "\"", "&#39;", "'", "&apos;", "'");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private EmailText() {
    }

    /**
     * What the customer wrote in a reply, without the quoted thread and the signature.
     */
    public static String withoutQuotes(String text) {
        if (text == null) {
            return "";
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (line.equals("-- ") || SEPARATOR.matcher(trimmed).matches() || attribution(lines, i)
                    || outlookHeader(lines, i)) {
                break;
            }
            if (trimmed.startsWith(">")) {
                continue;
            }
            kept.add(line.stripTrailing());
        }
        return BLANK_LINES.matcher(String.join("\n", kept)).replaceAll("\n\n").strip();
    }

    private static boolean attribution(String[] lines, int i) {
        String line = lines[i].strip();
        if (!ATTRIBUTION_START.matcher(line).matches()) {
            return false;
        }
        if (ATTRIBUTION_END.matcher(line).find()) {
            return true;
        }
        return i + 1 < lines.length && ATTRIBUTION_END.matcher(line + " " + lines[i + 1].strip()).find();
    }

    private static boolean outlookHeader(String[] lines, int i) {
        if (!HEADER_FROM.matcher(lines[i].strip()).matches()) {
            return false;
        }
        for (int next = i + 1; next < Math.min(lines.length, i + 4); next++) {
            if (HEADER_DATE.matcher(lines[next].strip()).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The text of an HTML body, for mail sent without a plain-text version; quoted messages are dropped.
     */
    public static String fromHtml(String html) {
        if (html == null) {
            return "";
        }
        String text = INVISIBLE.matcher(html).replaceAll("");
        text = QUOTES.matcher(text).replaceAll("");
        text = BLOCKS.matcher(text).replaceAll("\n");
        text = TAGS.matcher(text).replaceAll("");
        for (Map.Entry<String, String> entity : ENTITIES.entrySet()) {
            text = text.replace(entity.getKey(), entity.getValue());
        }
        Matcher numeric = NUMERIC_ENTITY.matcher(text);
        StringBuilder decoded = new StringBuilder();
        while (numeric.find()) {
            String replacement;
            try {
                int codePoint = Integer.parseInt(numeric.group(2), numeric.group(1).isEmpty() ? 10 : 16);
                replacement = Character.isValidCodePoint(codePoint) ? Character.toString(codePoint) : "";
            } catch (NumberFormatException e) {
                replacement = "";
            }
            numeric.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        numeric.appendTail(decoded);
        // &amp; last, so "&amp;lt;" stays "&lt;"
        String lines = decoded.toString().replace("&amp;", "&").replace(' ', ' ');
        StringBuilder result = new StringBuilder();
        for (String line : lines.split("\n", -1)) {
            result.append(line.strip()).append('\n');
        }
        return BLANK_LINES.matcher(result.toString()).replaceAll("\n\n").strip();
    }
}

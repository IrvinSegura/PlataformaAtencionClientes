package com.irvinsegura.supportchat.conversations.domain.model;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * What the assistant remembers of a customer's earlier conversations: the latest things they asked and were told, each
 * cut short, so a customer who comes back days later ("¿y lo del pedido que te dije?") does not have to start over.
 * <p>
 * Only what was said to the customer goes in: their messages, the answers the assistant gave from the documents and the
 * team's replies. Notices, "no sé" replies and failures say nothing about the customer. The team's notes never do.
 */
public final class CustomerMemory {

    /**
     * Longest part of one message that is remembered.
     */
    public static final int MAX_MESSAGE_CHARS = 300;

    /**
     * Longest memory sent to the model; the oldest lines go first when it would be longer.
     */
    public static final int MAX_CHARS = 2400;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private CustomerMemory() {
    }

    /**
     * @param customerName the name the customer is known by, or null
     * @param earlier      messages of the customer's earlier conversations, oldest first
     * @return the memory as lines of text, or an empty string when there is nothing worth remembering
     */
    public static String of(String customerName, List<Message> earlier) {
        Deque<String> lines = new ArrayDeque<>();
        int length = 0;
        // Newest first, so the most recent lines are the ones that fit
        for (int i = earlier.size() - 1; i >= 0; i--) {
            String line = line(earlier.get(i));
            if (line == null) {
                continue;
            }
            if (length + line.length() + 1 > MAX_CHARS) {
                break;
            }
            lines.addFirst(line);
            length += line.length() + 1;
        }
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder memory = new StringBuilder();
        if (customerName != null && !customerName.isBlank()) {
            memory.append("Customer's name: ").append(oneLine(customerName)).append('\n');
        }
        lines.forEach(line -> memory.append(line).append('\n'));
        return memory.toString().strip();
    }

    private static String line(Message message) {
        String who = switch (message.author()) {
            case VISITOR -> SmallTalk.of(message.content()).isPresent() ? null : "Customer";
            case ASSISTANT -> message.outcome() == AnswerOutcome.ANSWERED ? "Assistant" : null;
            case AGENT -> "Team";
            case SYSTEM -> null;
        };
        if (who == null) {
            return null;
        }
        String text = oneLine(message.content());
        if (text.length() > MAX_MESSAGE_CHARS) {
            text = text.substring(0, MAX_MESSAGE_CHARS).strip() + "…";
        }
        return "[" + DAY.format(message.createdAt()) + "] " + who + ": " + text;
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}

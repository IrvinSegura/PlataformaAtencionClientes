package com.irvinsegura.supportchat.conversations.domain.model;

/**
 * Sits between the streaming model and the visitor. The model signals "the sources don't say" by writing
 * {@link GroundedPrompt#NO_ANSWER_MARKER}, which must never reach the visitor; the guard holds back the first
 * characters until it can tell whether the answer starts with the marker, then passes everything else through
 * immediately. Not thread-safe: one guard per answer.
 */
public final class AnswerStreamGuard {

    private static final String MARKER = GroundedPrompt.NO_ANSWER_MARKER;

    private final StringBuilder held = new StringBuilder();
    private final StringBuilder full = new StringBuilder();
    private Decision decision = Decision.UNDECIDED;

    /**
     * @return the text that may be shown to the visitor now, possibly empty
     */
    public String accept(String delta) {
        full.append(delta);
        switch (decision) {
            case ANSWER -> {
                return delta;
            }
            case NO_ANSWER -> {
                return "";
            }
            case UNDECIDED -> {
                held.append(delta);
                String start = held.toString().stripLeading();
                if (start.startsWith(MARKER)) {
                    decision = Decision.NO_ANSWER;
                    return "";
                }
                if (start.length() >= MARKER.length() || !MARKER.startsWith(start)) {
                    decision = Decision.ANSWER;
                    String release = held.toString();
                    held.setLength(0);
                    return release;
                }
                return "";
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    /**
     * Ends the stream.
     *
     * @return text still held back that belongs to the answer, possibly empty
     */
    public String finish() {
        if (decision == Decision.UNDECIDED) {
            String start = held.toString().strip();
            // An answer shorter than the marker, or a truncated marker: only an exact marker means "no answer"
            decision = start.equals(MARKER) || start.isEmpty() ? Decision.NO_ANSWER : Decision.ANSWER;
            if (decision == Decision.ANSWER) {
                String release = held.toString();
                held.setLength(0);
                return release;
            }
        }
        return "";
    }

    public boolean isNoAnswer() {
        return decision == Decision.NO_ANSWER;
    }

    /**
     * Everything the model wrote, including what was held back.
     */
    public String fullText() {
        return full.toString();
    }

    private enum Decision {
        UNDECIDED,
        ANSWER,
        NO_ANSWER
    }
}

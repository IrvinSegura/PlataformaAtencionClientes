package com.irvinsegura.supportchat.channels.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mail has no buttons: a customer confirms or cancels an action the assistant proposed by replying to that message
 * with a word such as CONFIRMAR or CANCELAR. The proposal is sent with a Message-ID that carries the action's run, and
 * the customer's mail client quotes it back in In-Reply-To and References.
 *
 * @param confirmed false cancels the action
 */
public record EmailActionReply(UUID runId, boolean confirmed) {

    private static final Pattern RUN_IN_ID = Pattern.compile(
            "<action\\.([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.");
    private static final Set<String> CONFIRM = Set.of("confirmar", "confirmo", "confirm", "confirmed", "confirmer",
            "confirme", "si", "yes", "sim", "oui");
    private static final Set<String> CANCEL = Set.of("cancelar", "cancelo", "cancel", "annuler", "annule", "no",
            "nao", "non");
    // "No entiendo qué hace" is a question, not an answer
    private static final int MAX_ANSWER_WORDS = 3;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * The Message-ID to send a proposal with.
     *
     * @param nonce random text that keeps the id unique if the same action is proposed twice
     */
    public static String messageIdFor(UUID runId, String nonce, String domain) {
        return "<action." + runId + "." + nonce + "@" + domain + ">";
    }

    /**
     * The customer's answer to a proposal, when the mail replies to one and starts with a confirming or cancelling
     * word.
     */
    public static Optional<EmailActionReply> read(InboundEmail email) {
        Optional<UUID> runId = runRepliedTo(email);
        if (runId.isEmpty()) {
            return Optional.empty();
        }
        String firstLine = email.text().strip().lines().findFirst().orElse("");
        String folded = MARKS.matcher(Normalizer.normalize(firstLine.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        List<String> words = new ArrayList<>();
        for (String word : NON_WORD.split(folded.strip())) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        if (words.isEmpty() || words.size() > MAX_ANSWER_WORDS) {
            return Optional.empty();
        }
        if (CONFIRM.contains(words.getFirst())) {
            return Optional.of(new EmailActionReply(runId.get(), true));
        }
        if (CANCEL.contains(words.getFirst())) {
            return Optional.of(new EmailActionReply(runId.get(), false));
        }
        return Optional.empty();
    }

    // The message answered directly comes first; then the thread, newest first
    private static Optional<UUID> runRepliedTo(InboundEmail email) {
        List<String> ids = new ArrayList<>();
        if (email.inReplyTo() != null) {
            ids.add(email.inReplyTo());
        }
        ids.addAll(email.references().reversed());
        for (String id : ids) {
            Matcher matcher = RUN_IN_ID.matcher(id);
            if (matcher.find()) {
                return Optional.of(UUID.fromString(matcher.group(1)));
            }
        }
        return Optional.empty();
    }
}

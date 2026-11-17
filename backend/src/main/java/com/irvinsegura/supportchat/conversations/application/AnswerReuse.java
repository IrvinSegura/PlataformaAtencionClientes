package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.ReusableAnswer;
import com.irvinsegura.supportchat.conversations.domain.model.SmallTalk;
import com.irvinsegura.supportchat.conversations.domain.port.ReusableAnswerRepository;
import com.irvinsegura.supportchat.knowledge.KnowledgeSearch;
import com.irvinsegura.supportchat.shared.domain.QuestionKey;
import com.irvinsegura.supportchat.workspaces.AssistantProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives the answer the model already wrote to the same question again, without a model call. Many customers ask the
 * same things ("¿Cuánto cuesta el envío?"), and while the knowledge base, the actions and the assistant stay as they
 * were, the model would only write the same answer again.
 * <p>
 * Only questions that stand on their own are reused: when earlier messages may give the question its meaning ("¿y a
 * Monterrey?"), the answer belongs to that conversation. Answers that used an action belong to their customer and are
 * never kept. Nothing here ever stops an answer: when the store fails, the model simply answers.
 */
@Service
public class AnswerReuse {

    private static final Logger log = LoggerFactory.getLogger(AnswerReuse.class);

    private final ReusableAnswerRepository answers;
    private final KnowledgeSearch knowledge;
    private final Settings settings;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AnswerReuse(ReusableAnswerRepository answers, KnowledgeSearch knowledge, Settings settings,
            TransactionTemplate transactions, Clock clock) {
        this.answers = answers;
        this.knowledge = knowledge;
        this.settings = settings;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * What identifies the question's answer, or empty when it must be written for this conversation alone.
     *
     * @param history the earlier messages the model would read with the question
     */
    Optional<Key> keyFor(String question, List<Message> history, List<ActionDefinition> actions,
            AssistantProfile profile, ModelPreference preference) {
        if (!settings.enabled() || !standsAlone(history)) {
            return Optional.empty();
        }
        String questionKey = QuestionKey.of(question);
        // A key cut at its limit could match a longer, different question
        if (questionKey.isEmpty() || questionKey.length() >= QuestionKey.MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Key(questionKey, preference.name(),
                    digest(knowledge.revision(), actions, profile)));
        } catch (RuntimeException e) {
            log.warn("Could not tell whether an earlier answer can be reused", e);
            return Optional.empty();
        }
    }

    /**
     * The answer kept for that question, while it is recent enough to give again.
     */
    Optional<ReusableAnswer> find(Key key) {
        try {
            return transactions.execute(status -> answers.find(key.questionKey(), key.preference(),
                    key.contextDigest(), clock.instant().minus(settings.timeToLive())));
        } catch (RuntimeException e) {
            log.warn("Could not look for an earlier answer", e);
            return Optional.empty();
        }
    }

    void markReused(Key key) {
        try {
            transactions.executeWithoutResult(status -> answers.markReused(key.questionKey(), key.preference(),
                    key.contextDigest(), clock.instant()));
        } catch (RuntimeException e) {
            log.warn("Could not count a reused answer", e);
        }
    }

    /**
     * Keeps an answer the model just wrote, to give it again to the next customer who asks the same.
     */
    void keep(UUID workspaceId, Key key, String text, List<Citation> citations, String model, ModelTier tier) {
        Instant now = clock.instant();
        try {
            transactions.executeWithoutResult(status -> {
                answers.deleteWrittenBefore(now.minus(settings.timeToLive()));
                answers.save(workspaceId, new ReusableAnswer(key.questionKey(), key.preference(),
                        key.contextDigest(), text, citations, model, tier, now));
            });
        } catch (RuntimeException e) {
            log.warn("Could not keep an answer to reuse it", e);
        }
    }

    /**
     * Stops giving an answer again, e.g. because a customer said it did not help. Must run inside the caller's
     * transaction, with the workspace bound.
     */
    void forget(String text) {
        int forgotten = answers.forget(text);
        if (forgotten > 0) {
            log.info("Forgot {} reusable answers a customer said did not help", forgotten);
        }
    }

    /**
     * Stops giving an answer again, in a transaction of its own.
     */
    void discard(String text) {
        try {
            transactions.executeWithoutResult(status -> forget(text));
        } catch (RuntimeException e) {
            log.warn("Could not forget a reusable answer", e);
        }
    }

    /**
     * Whether the model would read nothing that changes what the question means: only notices, courtesy messages
     * and the assistant's courtesy replies.
     */
    static boolean standsAlone(List<Message> history) {
        return history.stream().allMatch(message -> switch (message.author()) {
            case SYSTEM -> true;
            case VISITOR -> SmallTalk.of(message.content()).isPresent();
            case ASSISTANT -> message.outcome() == AnswerOutcome.SMALL_TALK;
            case AGENT -> false;
        });
    }

    private static String digest(String knowledgeRevision, List<ActionDefinition> actions,
            AssistantProfile profile) {
        StringBuilder context = new StringBuilder()
                .append(knowledgeRevision).append('\n')
                .append(profile.workspaceName()).append('\n')
                .append(profile.botName()).append('\n');
        actions.stream()
                .sorted(Comparator.comparing(ActionDefinition::name))
                .forEach(action -> context.append(action).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(context.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /**
     * @param contextDigest a digest of the knowledge base, actions and assistant the answer is written with
     */
    record Key(String questionKey, String preference, String contextDigest) {
    }

    /**
     * @param enabled    whether answers are reused at all
     * @param timeToLive how long an answer is given again, even when nothing it depended on changed
     */
    public record Settings(boolean enabled, Duration timeToLive) {
    }
}

package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.DifficultQuestionRepeated;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.conversations.application.PlaygroundService.PlaygroundQuestion;
import com.irvinsegura.supportchat.conversations.application.PlaygroundService.PlaygroundResult;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.AttentionSettings;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Reason;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Status;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestionNotFoundException;
import com.irvinsegura.supportchat.conversations.domain.model.QuestionWords;
import com.irvinsegura.supportchat.conversations.domain.port.AttentionRepository;
import com.irvinsegura.supportchat.conversations.domain.port.DifficultQuestionRepository;
import com.irvinsegura.supportchat.conversations.domain.port.DifficultQuestionRepository.MeaningCandidate;
import com.irvinsegura.supportchat.conversations.domain.port.DifficultQuestionRepository.Recorded;
import com.irvinsegura.supportchat.conversations.domain.port.DifficultQuestionRepository.WordsCandidate;
import com.irvinsegura.supportchat.knowledge.QuestionMeaning;
import com.irvinsegura.supportchat.knowledge.TaughtAnswers;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.QuestionKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the assistant learns from the questions it struggled with. A question becomes difficult when the fast model
 * could not answer it, or when the customer showed the answer did not help; from then on the same question, in the
 * same or other words, goes straight to the capable model. The team sees them on the panel and can teach the right
 * answer, which goes into the knowledge base so every model finds it; the panel drafts that answer from the documents
 * for the team to correct.
 * <p>
 * Nobody teaching it must not cost the capable model forever: a question closes by itself once the capable model
 * answered it well several times in a row, or once nobody found it difficult for a while. One that keeps coming back
 * alerts the team by WhatsApp.
 * <p>
 * Two wordings are the same question when they share most of their words or, failing that, when they mean the same
 * by the knowledge base's embedding model. Learning never stops an answer: when the store or the embedding model
 * fails, the assistant answers as if it had learned nothing.
 */
@Service
public class DifficultQuestionService {

    private static final Logger log = LoggerFactory.getLogger(DifficultQuestionService.class);
    // Similarity of words from which two questions are the same one asked in other words
    static final double SAME_QUESTION_SIMILARITY = 0.6;
    // Questions compared by words with each new one; past these, the oldest are not looked at
    static final int CANDIDATES = 50;
    static final int MAX_ANSWER_CHARS = 4000;
    // A draft is taught as a document of its own, where "[1]" would point to nothing
    private static final Pattern CITATION_MARKER = Pattern.compile("\\s*\\[\\d{1,2}]");

    private final DifficultQuestionRepository questions;
    private final TaughtAnswers taughtAnswers;
    private final QuestionMeaning meaning;
    private final AttentionRepository attention;
    private final PlaygroundService playground;
    private final ApplicationEventPublisher events;
    private final Settings settings;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    DifficultQuestionService(DifficultQuestionRepository questions, TaughtAnswers taughtAnswers,
            QuestionMeaning meaning, AttentionRepository attention, PlaygroundService playground,
            ApplicationEventPublisher events, Settings settings, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.questions = questions;
        this.taughtAnswers = taughtAnswers;
        this.meaning = meaning;
        this.attention = attention;
        this.playground = playground;
        this.events = events;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * The open question customers found the answers to lacking before, this one or one asked in other words.
     *
     * @return its id, or empty when the question is not known as difficult
     */
    Optional<UUID> knownDifficult(String question) {
        Set<String> words = QuestionWords.of(question);
        if (words.size() < QuestionWords.MIN_WORDS) {
            return Optional.empty();
        }
        Instant activeSince = activeSince();
        try {
            Optional<UUID> byWords = readOnly.execute(status ->
                    sameByWords(questions.sharingWords(words, activeSince, CANDIDATES), words));
            if (byWords != null && byWords.isPresent()) {
                return byWords;
            }
            // Only a workspace with open questions pays for the embedding, which the knowledge search reuses anyway
            if (!Boolean.TRUE.equals(readOnly.execute(status -> questions.hasActive(activeSince)))) {
                return Optional.empty();
            }
            Optional<float[]> vector = meaning.of(question);
            if (vector.isEmpty()) {
                return Optional.empty();
            }
            Optional<UUID> byMeaning = readOnly.execute(status ->
                    sameByMeaning(questions.closestInMeaning(vector.get(), activeSince)));
            return byMeaning == null ? Optional.empty() : byMeaning;
        } catch (RuntimeException e) {
            log.warn("Could not tell whether a question was found difficult before", e);
            return Optional.empty();
        }
    }

    /**
     * Learns the question as difficult, in a transaction of its own.
     *
     * @param answer what the assistant answered, or null when it is not known
     */
    void learn(UUID workspaceId, String question, Reason reason, UUID conversationId, String answer) {
        try {
            // Embedded before the transaction, so no connection waits on the embedding model
            float[] vector = embeddable(question) ? meaning.of(question).orElse(null) : null;
            transactions.executeWithoutResult(status -> record(workspaceId, question, reason, conversationId,
                    answer, vector));
        } catch (RuntimeException e) {
            log.warn("Could not learn a difficult question of conversation {}", conversationId, e);
        }
    }

    /**
     * Learns the question as difficult inside the caller's transaction.
     */
    void record(UUID workspaceId, String question, Reason reason, UUID conversationId, String answer) {
        record(workspaceId, question, reason, conversationId, answer,
                embeddable(question) ? meaning.of(question).orElse(null) : null);
    }

    private void record(UUID workspaceId, String question, Reason reason, UUID conversationId, String answer,
            float[] vector) {
        String key = QuestionKey.of(question);
        if (key.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        String lastAnswer = answer == null || answer.length() <= MAX_ANSWER_CHARS ? answer
                : answer.substring(0, MAX_ANSWER_CHARS) + "…";
        Set<String> words = QuestionWords.of(question);
        Optional<Recorded> recorded = same(key, words, vector).flatMap(id -> questions.recordAgain(id,
                question.strip(), vector, reason, conversationId, lastAnswer, now));
        Recorded result = recorded.orElseGet(() -> questions.record(workspaceId, Ids.newId(), key, question.strip(),
                words, vector, reason, conversationId, lastAnswer, now));
        alertIfRepeated(workspaceId, result, question.strip(), now);
    }

    /**
     * The known question, whatever its status, that this one is: the same words in another order or case first, then
     * most of the same words, then the same meaning.
     */
    private Optional<UUID> same(String key, Set<String> words, float[] vector) {
        Optional<UUID> byKey = questions.findByKey(key);
        if (byKey.isPresent()) {
            return byKey;
        }
        if (words.size() < QuestionWords.MIN_WORDS) {
            return Optional.empty();
        }
        Optional<UUID> byWords = sameByWords(questions.sharingWords(words, null, CANDIDATES), words);
        if (byWords.isPresent() || vector == null) {
            return byWords;
        }
        return sameByMeaning(questions.closestInMeaning(vector, null));
    }

    private static Optional<UUID> sameByWords(List<WordsCandidate> candidates, Set<String> words) {
        return candidates.stream()
                .filter(candidate -> QuestionWords.similarity(words, candidate.words()) >= SAME_QUESTION_SIMILARITY)
                .max(Comparator.comparingDouble(candidate -> QuestionWords.similarity(words, candidate.words())))
                .map(WordsCandidate::id);
    }

    private Optional<UUID> sameByMeaning(Optional<MeaningCandidate> closest) {
        return closest.filter(candidate -> candidate.similarity() >= sameQuestionSimilarity())
                .map(MeaningCandidate::id);
    }

    // Too short to say what it asks, like "¿y eso?": never matched with another question
    private static boolean embeddable(String question) {
        return QuestionWords.of(question).size() >= QuestionWords.MIN_WORDS;
    }

    private double sameQuestionSimilarity() {
        return settings.sameQuestionSimilarity() != null ? settings.sameQuestionSimilarity()
                : meaning.sameQuestionSimilarity();
    }

    /**
     * Tells the team, once, that an open question keeps coming back, when the workspace alerts someone. Delivered
     * after the caller's transaction commits.
     */
    private void alertIfRepeated(UUID workspaceId, Recorded recorded, String question, Instant now) {
        if (recorded.status() != Status.OPEN || recorded.alerted()
                || recorded.occurrences() < settings.alertAfter()) {
            return;
        }
        AttentionSettings alerts = attention.findSettings().orElse(AttentionSettings.DEFAULTS);
        List<String> recipients = alerts.difficultQuestionRecipients();
        // Not noted when nobody is alerted, so numbers given later still hear of it the next time it comes back
        if (recipients.isEmpty() || !questions.markAlerted(recorded.id(), settings.alertAfter(), now)) {
            return;
        }
        events.publishEvent(new DifficultQuestionRepeated(workspaceId, recorded.id(), shorten(question),
                recorded.occurrences(), recipients, alerts.template(), alerts.templateLanguage(), now));
    }

    private static String shorten(String text) {
        return text.length() <= AttentionService.MAX_EXCERPT ? text
                : text.substring(0, AttentionService.MAX_EXCERPT - 1) + "…";
    }

    /**
     * The capable model answered a known difficult question; after enough in a row with no sign of difficulty, it
     * closes by itself and the fast model may try it again. A sign of difficulty later opens it again.
     */
    void answeredByCapableModel(UUID questionId) {
        try {
            Boolean closed = transactions.execute(status -> questions.answeredWell(questionId,
                    settings.answeredWellToClose(), clock.instant()));
            if (Boolean.TRUE.equals(closed)) {
                log.info("Difficult question {} closed by itself: the capable model answered it {} times in a row",
                        questionId, settings.answeredWellToClose());
            }
        } catch (RuntimeException e) {
            log.warn("Could not count an answer to the difficult question {}", questionId, e);
        }
    }

    /**
     * The questions with the given status. The open ones that went quiet are closed first, so the list says what
     * still costs the capable model.
     */
    @Transactional
    public DifficultQuestionPageView page(Status status, int page, int size) {
        int closed = questions.closeQuiet(activeSince(), clock.instant());
        if (closed > 0) {
            log.info("{} difficult questions closed by themselves after {} without being found difficult", closed,
                    settings.quietPeriod());
        }
        DifficultQuestionRepository.Page result = questions.page(status, page, size);
        return new DifficultQuestionPageView(result.items(), page, size, result.total());
    }

    @Transactional
    public void changeStatus(UUID id, Status status) {
        if (!questions.changeStatus(id, status, clock.instant())) {
            throw new DifficultQuestionNotFoundException();
        }
    }

    /**
     * Puts the team's answer into the knowledge base and closes the question: from now on any model finds the answer,
     * so the question no longer needs the capable one.
     *
     * @return the id of the document that holds the answer
     */
    @Transactional
    public UUID teach(UUID id, String answer, UUID taughtBy) {
        DifficultQuestion question = questions.findById(id).orElseThrow(DifficultQuestionNotFoundException::new);
        UUID documentId = taughtAnswers.teach(question.question(), answer, taughtBy);
        questions.changeStatus(id, Status.TAUGHT, clock.instant());
        return documentId;
    }

    /**
     * An answer to the question written by the capable model from the knowledge base, for the team to correct before
     * teaching it. It counts as a playground answer of the plan.
     *
     * @return empty when the documents do not hold the answer
     * @throws PlaygroundLimitReachedException when the plan's answers for the month are used up
     */
    public Optional<String> draft(UUID id) {
        DifficultQuestion question = readOnly.execute(status -> questions.findById(id))
                .orElseThrow(DifficultQuestionNotFoundException::new);
        // The model is called with no transaction open
        PlaygroundQuestion asked = playground.prepare(question.question(), List.of(), ModelPreference.PRECISE);
        PlaygroundResult result = playground.answer(asked, text -> {
        });
        if (result.outcome() != AnswerOutcome.ANSWERED || !result.toolCalls().isEmpty()) {
            // An answer that needs one of the workspace's actions belongs to each customer, not to the documents
            return Optional.empty();
        }
        String draft = CITATION_MARKER.matcher(result.text()).replaceAll("").strip();
        if (draft.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(draft.length() <= MAX_ANSWER_CHARS ? draft : draft.substring(0, MAX_ANSWER_CHARS));
    }

    private Instant activeSince() {
        return clock.instant().minus(settings.quietPeriod());
    }

    public record DifficultQuestionPageView(List<DifficultQuestion> items, int page, int size, long total) {
    }

    /**
     * How difficult questions are learned; tune them with the Results page once there are a few weeks of answers.
     *
     * @param answeredWellToClose    answers in a row of the capable model, with no sign of difficulty, that close a
     *                               question by itself
     * @param quietPeriod            how long a question nobody found difficult stays open
     * @param alertAfter             times found difficult from which the team is alerted by WhatsApp
     * @param sameQuestionSimilarity cosine similarity from which two questions are the same; null uses the embedding
     *                               model's own value
     */
    public record Settings(int answeredWellToClose, Duration quietPeriod, int alertAfter,
            Double sameQuestionSimilarity) {
    }
}

package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.QuestionUnanswered;
import com.irvinsegura.supportchat.knowledge.TaughtAnswers;
import com.irvinsegura.supportchat.knowledge.TopicGrouping;
import com.irvinsegura.supportchat.knowledge.domain.model.GapStatus;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeGapNotFoundException;
import com.irvinsegura.supportchat.knowledge.domain.port.KnowledgeGapRepository;
import com.irvinsegura.supportchat.knowledge.domain.port.KnowledgeGapRepository.GapSummary;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.QuestionKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The knowledge gaps report: what visitors asked that the knowledge base could not answer, most asked first.
 */
@Service
public class KnowledgeGapService {

    // The most asked open questions are grouped; past these, the rest are asked too rarely to shape a document
    static final int SUGGESTION_SOURCE_LIMIT = 200;

    private final KnowledgeGapRepository gaps;
    private final TopicGrouping topics;
    private final TaughtAnswers taughtAnswers;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    KnowledgeGapService(KnowledgeGapRepository gaps, TopicGrouping topics, TaughtAnswers taughtAnswers,
            PlatformTransactionManager transactions, Clock clock) {
        this.taughtAnswers = taughtAnswers;
        this.gaps = gaps;
        this.topics = topics;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @Transactional
    public void record(QuestionUnanswered event) {
        gaps.recordOccurrence(event.workspaceId(), Ids.newId(), QuestionKey.of(event.question()), event.question(),
                event.conversationId(), event.messageId(), event.askedAt());
    }

    @Transactional(readOnly = true)
    public GapPageView page(GapStatus status, int page, int size) {
        KnowledgeGapRepository.GapPage result = gaps.page(status, page, size);
        return new GapPageView(result.gaps(), page, size, result.total());
    }

    @Transactional
    public void changeStatus(UUID gapId, GapStatus status) {
        if (!gaps.changeStatus(gapId, status, clock.instant())) {
            throw new KnowledgeGapNotFoundException();
        }
    }

    /**
     * Puts the team's answer to the question into the knowledge base and marks the gap resolved.
     *
     * @return the id of the document that holds the answer
     */
    @Transactional
    public UUID teach(UUID gapId, String answer, UUID taughtBy) {
        String question = gaps.question(gapId).orElseThrow(KnowledgeGapNotFoundException::new);
        UUID documentId = taughtAnswers.teach(question, answer, taughtBy);
        gaps.changeStatus(gapId, GapStatus.RESOLVED, clock.instant());
        return documentId;
    }

    /**
     * Documents worth writing: the open questions grouped by topic, the topics asked the most first. Each topic is
     * named after its most asked question.
     */
    public List<DocumentSuggestion> suggestions() {
        // Read in a transaction of its own, then grouped outside it: the embedding provider may be slow
        List<GapSummary> open = readOnly.execute(status ->
                gaps.page(GapStatus.OPEN, 0, SUGGESTION_SOURCE_LIMIT).gaps());
        if (open.isEmpty()) {
            return List.of();
        }
        List<List<Integer>> groups = topics.group(open.stream().map(GapSummary::question).toList());
        return groups.stream()
                .map(group -> DocumentSuggestion.of(group.stream().map(open::get).toList()))
                .sorted(Comparator.comparingLong(DocumentSuggestion::occurrences).reversed()
                        .thenComparing(DocumentSuggestion::lastAskedAt, Comparator.reverseOrder()))
                .toList();
    }

    public record GapPageView(List<GapSummary> items, int page, int size, long total) {
    }

    /**
     * @param title       the most asked question of the topic
     * @param occurrences how many times its questions were asked in all
     * @param questions   most asked first
     */
    public record DocumentSuggestion(String title, long occurrences, Instant lastAskedAt,
            List<GapSummary> questions) {

        static DocumentSuggestion of(List<GapSummary> questions) {
            return new DocumentSuggestion(questions.getFirst().question(),
                    questions.stream().mapToLong(GapSummary::occurrences).sum(),
                    questions.stream().map(GapSummary::lastAskedAt).max(Comparator.naturalOrder()).orElseThrow(),
                    List.copyOf(questions));
        }
    }
}

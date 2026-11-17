package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.application.OutcomeReport.ModelQuality;
import com.irvinsegura.supportchat.conversations.application.OutcomeReport.OutcomeDay;
import com.irvinsegura.supportchat.conversations.application.OutcomeReport.RatingComment;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.InvalidPeriodException;
import com.irvinsegura.supportchat.conversations.domain.port.DifficultQuestionRepository;
import com.irvinsegura.supportchat.conversations.domain.port.OutcomeRepository;
import com.irvinsegura.supportchat.conversations.domain.port.OutcomeRepository.DayOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outcomes report of the workspace bound to the current code: how many conversations the assistant solved alone,
 * how many needed a person, and what customers said.
 */
@Service
public class OutcomeService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    private static final int COMMENTS = 10;

    private final OutcomeRepository outcomes;
    private final DifficultQuestionRepository difficult;
    private final Clock clock;

    OutcomeService(OutcomeRepository outcomes, DifficultQuestionRepository difficult, Clock clock) {
        this.outcomes = outcomes;
        this.difficult = difficult;
        this.clock = clock;
    }

    /**
     * @param from first UTC day, or null for {@value #DEFAULT_DAYS} days before {@code to}
     * @param to   last UTC day, included, or null for today
     */
    @Transactional(readOnly = true)
    public OutcomeReport report(LocalDate from, LocalDate to) {
        LocalDate last = to != null ? to : LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1);
        if (first.isAfter(last) || ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
            throw new InvalidPeriodException(MAX_DAYS);
        }
        Instant start = first.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<OutcomeDay> days = days(outcomes.conversations(start, end));
        long resolved = days.stream().mapToLong(OutcomeDay::resolvedByAssistant).sum();
        long handedOff = days.stream().mapToLong(OutcomeDay::handedOff).sum();
        long unresolved = days.stream().mapToLong(OutcomeDay::unresolved).sum();
        long conversations = resolved + handedOff + unresolved;

        Map<AnswerOutcome, Long> answers = outcomes.answers(start, end);
        Map<Boolean, Long> verdicts = outcomes.answerFeedback(start, end);
        long helpful = verdicts.getOrDefault(true, 0L);
        long unhelpful = verdicts.getOrDefault(false, 0L);

        List<ModelQuality> models = outcomes.modelAnswers(start, end).stream()
                .map(row -> new ModelQuality(row.tier(), row.reason(), row.answers(), row.notAnswered(), row.helpful(),
                        row.unhelpful(), share(row.unhelpful(), row.helpful() + row.unhelpful())))
                .toList();

        Map<Integer, Long> scores = outcomes.ratings(start, end);
        List<Long> ratingCounts = IntStream.rangeClosed(1, 5).mapToObj(score -> scores.getOrDefault(score, 0L))
                .toList();
        long ratings = ratingCounts.stream().mapToLong(Long::longValue).sum();
        long scoreSum = IntStream.rangeClosed(1, 5).mapToLong(score -> score * ratingCounts.get(score - 1)).sum();
        long satisfied = ratingCounts.get(3) + ratingCounts.get(4);

        List<RatingComment> comments = outcomes.comments(start, end, COMMENTS).stream()
                .map(comment -> new RatingComment(comment.conversationId(), comment.score(), comment.comment(),
                        comment.ratedAt()))
                .toList();
        return new OutcomeReport(first, last, conversations, resolved, handedOff, unresolved,
                share(resolved, conversations), answers.getOrDefault(AnswerOutcome.ANSWERED, 0L),
                answers.getOrDefault(AnswerOutcome.NO_ANSWER, 0L), answers.getOrDefault(AnswerOutcome.FAILED, 0L),
                helpful, unhelpful, share(helpful, helpful + unhelpful), ratings,
                ratings == 0 ? null : (double) scoreSum / ratings, share(satisfied, ratings), ratingCounts, days,
                comments, models, difficult.countFirstSeen(start, end));
    }

    /**
     * Days without conversations are left out: the panel draws what happened, and an empty month stays short.
     */
    private static List<OutcomeDay> days(List<DayOutcome> rows) {
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (DayOutcome row : rows) {
            long[] counts = byDay.computeIfAbsent(row.day(), day -> new long[ConversationOutcome.values().length]);
            counts[row.outcome().ordinal()] += row.conversations();
        }
        List<OutcomeDay> days = new ArrayList<>(byDay.size());
        byDay.forEach((day, counts) -> {
            long resolved = counts[ConversationOutcome.RESOLVED.ordinal()];
            long handedOff = counts[ConversationOutcome.HANDED_OFF.ordinal()];
            long unresolved = counts[ConversationOutcome.UNRESOLVED.ordinal()];
            days.add(new OutcomeDay(day, resolved + handedOff + unresolved, resolved, handedOff, unresolved));
        });
        return days;
    }

    private static Double share(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }
}

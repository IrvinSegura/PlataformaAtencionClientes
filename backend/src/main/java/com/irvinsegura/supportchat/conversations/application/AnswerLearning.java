package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.application.AnswerService.PreparedQuestion;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerDissatisfaction;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Reason;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.MessageAuthor;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter.Routing;
import com.irvinsegura.supportchat.conversations.domain.model.ModelSelection;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.SmallTalk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What answering learns about difficult questions: before an answer, from the customer's reply to the last one and
 * from earlier conversations; after it, from which model managed to answer.
 */
@Component
class AnswerLearning {

    private final DifficultQuestionService difficult;
    private final AnswerReuse reuse;

    AnswerLearning(DifficultQuestionService difficult, AnswerReuse reuse) {
        this.difficult = difficult;
        this.reuse = reuse;
    }

    /**
     * Learns from what the customer's message says about the assistant's last answer, and from what earlier
     * conversations taught.
     *
     * @return the routing to use instead of the router's when the fast model is not enough, or null
     */
    Learned before(PreparedQuestion question, List<Message> history, ModelSelection selection) {
        Optional<Exchange> last = lastExchange(history);
        if (last.isPresent()) {
            Exchange exchange = last.get();
            Reason reason = AnswerDissatisfaction.saysSo(question.text()) ? Reason.UNSATISFIED
                    : AnswerDissatisfaction.asksAgain(exchange.question(), question.text()) ? Reason.ASKED_AGAIN
                    : null;
            if (reason != null) {
                difficult.learn(question.workspaceId(), exchange.question(), reason, question.conversationId(),
                        exchange.answer());
                // Not given again to the next customer who asks the same
                reuse.discard(exchange.answer());
                return new Learned(ModelRouter.learned(true), null);
            }
        }
        if (selection.mayEscalate()) {
            Optional<UUID> known = difficult.knownDifficult(question.text());
            if (known.isPresent()) {
                return new Learned(ModelRouter.learned(false), known.get());
            }
        }
        return null;
    }

    /**
     * @param learned what {@link #before} learned, or null
     * @param tier    the model that wrote the answer
     */
    void after(PreparedQuestion question, Learned learned, Routing routing, AnswerOutcome outcome, ModelTier tier,
            String text) {
        if (outcome != AnswerOutcome.ANSWERED) {
            return;
        }
        if (ModelRouter.ESCALATED.equals(routing.reason())) {
            // The fast model could not answer it: next time the capable one answers first, without the failed try
            difficult.learn(question.workspaceId(), question.text(), Reason.ESCALATED, question.conversationId(),
                    text);
        }
        if (learned != null && learned.difficultQuestionId() != null && tier == ModelTier.COMPLEX) {
            // Counted now and taken back by the next sign of difficulty, which starts the run of good answers over
            difficult.answeredByCapableModel(learned.difficultQuestionId());
        }
    }

    /**
     * The customer's last question and the assistant's answer to it, when the answer is the last thing said and came
     * from the documents (not an action, a courtesy reply or a "no sé").
     */
    static Optional<Exchange> lastExchange(List<Message> history) {
        int i = history.size() - 1;
        while (i >= 0 && history.get(i).author() == MessageAuthor.SYSTEM) {
            i--;
        }
        if (i < 0) {
            return Optional.empty();
        }
        Message answer = history.get(i);
        if (answer.author() != MessageAuthor.ASSISTANT || answer.outcome() != AnswerOutcome.ANSWERED
                || answer.actionRunId() != null) {
            return Optional.empty();
        }
        for (i--; i >= 0; i--) {
            Message message = history.get(i);
            if (message.author() == MessageAuthor.VISITOR) {
                return SmallTalk.of(message.content()).isPresent() ? Optional.empty()
                        : Optional.of(new Exchange(message.content(), answer.content()));
            }
            if (message.author() != MessageAuthor.SYSTEM) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the assistant's last reply asked the customer something.
     */
    static boolean assistantAsked(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            if (message.author() == MessageAuthor.ASSISTANT || message.author() == MessageAuthor.AGENT) {
                return message.content().strip().matches("(?s).*\\?\\W*");
            }
        }
        return false;
    }

    record Exchange(String question, String answer) {
    }

    /**
     * @param difficultQuestionId the known difficult question this one is, or null when the customer's message itself
     *                            showed the last answer did not help
     */
    record Learned(Routing routing, UUID difficultQuestionId) {
    }
}

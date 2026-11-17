package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter;
import com.irvinsegura.supportchat.conversations.domain.model.SmallTalk;
import com.irvinsegura.supportchat.conversations.domain.model.SourceSelection;
import com.irvinsegura.supportchat.shared.domain.Language;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Tunable behaviour of the assistant, built from configuration.
 *
 * @param historyMessages  earlier messages sent along so follow-up questions make sense
 * @param maxQuestionChars longest question accepted
 * @param maxQuestions     questions one conversation may ask
 * @param visitorTokenTtl  inactivity after which the visitor's token stops working, and after which a channel
 *                         customer who writes again starts a new conversation
 * @param router           chooses the model tier for each question
 * @param escalateNoAnswer ask the capable model again when the cheap one finds no answer in relevant sources
 * @param maxToolRounds    times the model may use actions before it must answer
 * @param defaultLanguage  the language of conversations whose customer has not shown theirs yet
 * @param replies          the fixed replies in each language; at least in the default one
 */
public record ConversationSettings(SourceSelection sourceSelection, int historyMessages, int maxQuestionChars,
        int maxQuestions, Duration visitorTokenTtl, ModelRouter router, boolean escalateNoAnswer, int maxToolRounds,
        Language defaultLanguage, Map<Language, Replies> replies) {

    public ConversationSettings {
        Objects.requireNonNull(defaultLanguage);
        replies = Map.copyOf(replies);
        if (!replies.containsKey(defaultLanguage)) {
            throw new IllegalArgumentException("The replies must include the default language, " + defaultLanguage);
        }
    }

    /**
     * The language the platform words its own texts in for this conversation.
     */
    public Language languageOf(Conversation conversation) {
        return conversation.language() != null ? conversation.language() : defaultLanguage;
    }

    /**
     * The fixed replies in that language, or in the default one when it has none.
     */
    public Replies replies(Language language) {
        Replies localized = language == null ? null : replies.get(language);
        return localized != null ? localized : replies.get(defaultLanguage);
    }

    /**
     * What the customer reads when no model writes the reply.
     *
     * @param noAnswer      the knowledge base has no answer
     * @param failure       the answer could not be produced
     * @param confirmPrompt shown with the buttons when the model proposed an action without saying anything
     * @param done          after a confirmed action ran, when the business's system sent no message of its own
     * @param failed        after a confirmed action failed
     * @param cancelled     after the customer cancelled an action
     * @param expired       after the customer confirmed too late
     * @param greeting      to a message that only greets
     * @param thanks        to a message that only thanks
     * @param goodbye       to a message that only says goodbye
     * @param acknowledged  to a message that only acknowledges, such as "ok" or "perfecto"
     */
    public record Replies(String noAnswer, String failure, String confirmPrompt, String done, String failed,
            String cancelled, String expired, String greeting, String thanks, String goodbye, String acknowledged) {

        /**
         * The courtesy reply to a message of that kind.
         */
        public String to(SmallTalk.Kind kind) {
            return switch (kind) {
                case GREETING -> greeting;
                case THANKS -> thanks;
                case GOODBYE -> goodbye;
                case ACKNOWLEDGEMENT -> acknowledged;
            };
        }
    }
}

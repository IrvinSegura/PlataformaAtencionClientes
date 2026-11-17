package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.domain.model.ConversationEvent;

/**
 * Carries conversation events to every running instance, this one included, so a change reaches the people
 * watching it whichever instance their stream is connected to.
 */
public interface ConversationEventBus {

    /**
     * Sends the event once the current transaction commits; nothing is sent if it rolls back. Outside a
     * transaction it is sent at once.
     */
    void publish(ConversationEvent event);

    /**
     * Subscribers are called one event at a time, in the order the transactions committed.
     */
    void subscribe(Subscriber subscriber);

    interface Subscriber {

        void deliver(ConversationEvent event);

        /**
         * The bus lost its connection and events may have been missed while it reconnected. Whoever keeps state
         * derived from events should refresh it.
         */
        void eventsMissed();
    }
}

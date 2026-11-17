/**
 * Visitor conversations through the widget: each question is answered by a language model grounded on the
 * workspace's knowledge base, streamed as it is written, with numbered citations. Questions the knowledge base cannot
 * answer get an honest "I don't know" and are published as
 * {@link com.irvinsegura.supportchat.knowledge.QuestionUnanswered}, which the knowledge module groups into gaps.
 * The visitor can ask for a person: a member of the team takes the conversation over and both sides see the change
 * live through server-sent events.
 * <p>
 * Difficult questions and the outcomes report stay here on purpose: the first decide, while each question is answered,
 * which model answers it, and the second is a read model over this module's own messages, ratings and feedback.
 * Follow-ups, which only write to a customer later, live in their own module behind {@link CustomerMessages}.
 * <p>
 * It is the largest module because the conversation is the product's core, not because it mixes unrelated work:
 * customers, attention alerts, difficult questions, reusable answers and outcomes all read and change the
 * conversation and its messages. Attention alerts were weighed as a module of their own and kept here: spotting an
 * upset customer hands the conversation to the team, and one settings row holds both that choice and whom to alert,
 * so a split would put a call to another module in every answer and need a copy of the contact in the alerts for
 * little gain. Size is kept down inside the module instead, with one class per step of answering.
 */
@ApplicationModule(displayName = "Conversations")
package com.irvinsegura.supportchat.conversations;

import org.springframework.modulith.ApplicationModule;

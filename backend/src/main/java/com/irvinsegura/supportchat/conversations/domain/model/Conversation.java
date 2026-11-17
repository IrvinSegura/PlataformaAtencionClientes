package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationIntent;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A chat between one customer and the assistant, or a member of the team once the customer asks for a person.
 * Web visitors are anonymous and prove ownership with a secret token whose hash is stored here; the token stops
 * working after a period of inactivity. Customers on other channels (WhatsApp) are identified by the contact the
 * channel verified, such as their phone number, and hold no token.
 * <p>
 * Methods that change the handling return whether anything changed, so repeating a request (a double click, a retry)
 * is harmless and records no second notice.
 */
public final class Conversation {

    public static final int MAX_ORIGIN_LENGTH = 255;
    public static final int MAX_CONTACT_NAME_LENGTH = 120;

    private final UUID id;
    private final UUID workspaceId;
    private final byte[] visitorTokenHash;
    private ConversationStatus status;
    private int questionCount;
    private final String origin;
    private final Instant startedAt;
    private Instant lastActivityAt;
    private ConversationHandling handling;
    private UUID agentId;
    private String agentName;
    private Instant handoffRequestedAt;
    private final ConversationChannel channel;
    private final String contactId;
    private final String contactName;
    private Language language;
    private Instant frustratedAt;
    private final Set<ConversationIntent> intents = EnumSet.noneOf(ConversationIntent.class);
    private boolean intentsEdited;

    private Conversation(UUID id, UUID workspaceId, byte[] visitorTokenHash, ConversationStatus status,
            int questionCount, String origin, Instant startedAt, Instant lastActivityAt,
            ConversationHandling handling, UUID agentId, String agentName, Instant handoffRequestedAt,
            ConversationChannel channel, String contactId, String contactName) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.channel = Objects.requireNonNull(channel);
        if ((channel == ConversationChannel.WEB) != (visitorTokenHash != null)
                || (channel == ConversationChannel.WEB) != (contactId == null)) {
            throw new IllegalArgumentException("Web conversations have a visitor token; the others, a contact");
        }
        this.visitorTokenHash = visitorTokenHash == null ? null : visitorTokenHash.clone();
        this.contactId = contactId;
        this.contactName = contactName;
        this.status = Objects.requireNonNull(status);
        this.questionCount = questionCount;
        this.origin = origin;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt);
        this.handling = Objects.requireNonNull(handling);
        this.agentId = agentId;
        this.agentName = agentName;
        this.handoffRequestedAt = handoffRequestedAt;
    }

    /**
     * @param origin the web origin the widget ran on, kept for the operator's context; not trusted for anything
     */
    public static Conversation start(UUID id, UUID workspaceId, byte[] visitorTokenHash, String origin, Instant now) {
        String safeOrigin = origin == null || origin.isBlank() ? null
                : origin.strip().substring(0, Math.min(origin.strip().length(), MAX_ORIGIN_LENGTH));
        return new Conversation(id, workspaceId, Objects.requireNonNull(visitorTokenHash), ConversationStatus.OPEN, 0,
                safeOrigin, now, now, ConversationHandling.ASSISTANT, null, null, null, ConversationChannel.WEB, null,
                null);
    }

    /**
     * Starts a conversation with a customer the channel identified, e.g. by their WhatsApp number.
     *
     * @param contactName the name the customer shows on that channel, kept for the operator; may be null
     */
    public static Conversation startOnChannel(UUID id, UUID workspaceId, ConversationChannel channel,
            String contactId, String contactName, Instant now) {
        if (channel == ConversationChannel.WEB || contactId == null || contactId.isBlank()) {
            throw new IllegalArgumentException("A channel conversation needs a channel other than the web and a "
                    + "contact");
        }
        String name = contactName == null || contactName.isBlank() ? null
                : contactName.strip().substring(0, Math.min(contactName.strip().length(), MAX_CONTACT_NAME_LENGTH));
        return new Conversation(id, workspaceId, null, ConversationStatus.OPEN, 0, null, now, now,
                ConversationHandling.ASSISTANT, null, null, null, channel, contactId.strip(), name);
    }

    public static Conversation restore(UUID id, UUID workspaceId, byte[] visitorTokenHash, ConversationStatus status,
            int questionCount, String origin, Instant startedAt, Instant lastActivityAt,
            ConversationHandling handling, UUID agentId, String agentName, Instant handoffRequestedAt,
            ConversationChannel channel, String contactId, String contactName, Language language) {
        return restore(id, workspaceId, visitorTokenHash, status, questionCount, origin, startedAt, lastActivityAt,
                handling, agentId, agentName, handoffRequestedAt, channel, contactId, contactName, language, null);
    }

    /**
     * @param frustratedAt when the customer first seemed upset, or null
     */
    public static Conversation restore(UUID id, UUID workspaceId, byte[] visitorTokenHash, ConversationStatus status,
            int questionCount, String origin, Instant startedAt, Instant lastActivityAt,
            ConversationHandling handling, UUID agentId, String agentName, Instant handoffRequestedAt,
            ConversationChannel channel, String contactId, String contactName, Language language,
            Instant frustratedAt) {
        return restore(id, workspaceId, visitorTokenHash, status, questionCount, origin, startedAt, lastActivityAt,
                handling, agentId, agentName, handoffRequestedAt, channel, contactId, contactName, language,
                frustratedAt, Set.of(), false);
    }

    /**
     * @param intents       what the conversation is about
     * @param intentsEdited whether the team set them by hand, which stops the automatic tagging
     */
    public static Conversation restore(UUID id, UUID workspaceId, byte[] visitorTokenHash, ConversationStatus status,
            int questionCount, String origin, Instant startedAt, Instant lastActivityAt,
            ConversationHandling handling, UUID agentId, String agentName, Instant handoffRequestedAt,
            ConversationChannel channel, String contactId, String contactName, Language language,
            Instant frustratedAt, Set<ConversationIntent> intents, boolean intentsEdited) {
        Conversation conversation = new Conversation(id, workspaceId, visitorTokenHash, status, questionCount, origin,
                startedAt, lastActivityAt, handling, agentId, agentName, handoffRequestedAt, channel, contactId,
                contactName);
        conversation.language = language;
        conversation.frustratedAt = frustratedAt;
        conversation.intents.addAll(intents);
        conversation.intentsEdited = intentsEdited;
        return conversation;
    }

    /**
     * Adds what a customer's message is about. Once the team set the topics by hand, theirs stand.
     *
     * @return whether any topic was added
     */
    public boolean tagIntents(Set<ConversationIntent> found) {
        return !intentsEdited && intents.addAll(found);
    }

    /**
     * The team sets what the conversation is about, correcting the automatic tags; later messages add none.
     */
    public void setIntents(Set<ConversationIntent> chosen) {
        intents.clear();
        intents.addAll(chosen);
        intentsEdited = true;
    }

    /**
     * Notes that the customer seems upset. Only the first time counts, so they alert the team once.
     *
     * @return whether this is the first time
     */
    public boolean markFrustrated(Instant now) {
        if (frustratedAt != null) {
            return false;
        }
        frustratedAt = now;
        return true;
    }

    /**
     * Takes the language the customer writes in, when known: a hint from their browser at the start, then whatever
     * their messages are written in. Null leaves it as it was.
     */
    public void speak(Language language) {
        if (language != null) {
            this.language = language;
        }
    }

    /**
     * Checks the visitor's token in constant time. A wrong token and an expired one look the same to the caller.
     */
    public void authenticate(String visitorToken, Duration tokenTtl, Instant now) {
        // Channel conversations have no token, so no token opens them through the widget
        if (visitorTokenHash == null || !SecretTokens.matches(visitorToken, visitorTokenHash)) {
            throw new InvalidConversationTokenException();
        }
        if (!lastActivityAt.plus(tokenTtl).isAfter(now)) {
            throw new InvalidConversationTokenException();
        }
    }

    /**
     * Registers a visitor message. Only questions for the assistant count towards the per-conversation limit, which
     * caps what one visitor can spend on the language model; messages for a person cost nothing to answer.
     */
    public void acceptQuestion(int maxQuestions, Instant now) {
        requireOpen();
        if (handling == ConversationHandling.ASSISTANT) {
            if (questionCount >= maxQuestions) {
                throw new QuestionLimitReachedException();
            }
            questionCount++;
        }
        lastActivityAt = now;
    }

    /**
     * A channel conversation the customer comes back to after a long silence is better continued in a new one: the
     * old context no longer helps the assistant. Using up the questions does not start a new one, or the limit on
     * what one customer can spend would not hold.
     */
    public boolean shouldStartOver(Duration inactivity, Instant now) {
        return status == ConversationStatus.CLOSED || !lastActivityAt.plus(inactivity).isAfter(now);
    }

    /**
     * The assistant has answered all the questions it may in this conversation.
     */
    public boolean usedUpQuestions(int maxQuestions) {
        return handling == ConversationHandling.ASSISTANT && questionCount >= maxQuestions;
    }

    /**
     * Closes a channel conversation the customer is starting over, with no member involved.
     */
    public void retire(Instant now) {
        if (handling == ConversationHandling.WAITING) {
            handling = ConversationHandling.ASSISTANT;
            handoffRequestedAt = null;
        }
        status = ConversationStatus.CLOSED;
        lastActivityAt = now;
    }

    public boolean isAnsweredByAssistant() {
        return handling == ConversationHandling.ASSISTANT;
    }

    /**
     * The visitor asks for a person; the assistant stops answering until someone takes over or the visitor cancels.
     */
    public boolean requestHandoff(Instant now) {
        requireOpen();
        if (handling != ConversationHandling.ASSISTANT) {
            return false;
        }
        handling = ConversationHandling.WAITING;
        handoffRequestedAt = now;
        lastActivityAt = now;
        return true;
    }

    /**
     * The visitor no longer waits for a person and goes back to the assistant.
     */
    public boolean cancelHandoff(Instant now) {
        if (handling != ConversationHandling.WAITING) {
            return false;
        }
        handling = ConversationHandling.ASSISTANT;
        handoffRequestedAt = null;
        lastActivityAt = now;
        return true;
    }

    /**
     * A member starts answering, whether or not the visitor asked for a person.
     */
    public boolean takeOver(UUID memberId, String memberName, Instant now) {
        requireOpen();
        if (handling == ConversationHandling.AGENT) {
            if (agentId.equals(memberId)) {
                return false;
            }
            throw new ConversationAssignedException();
        }
        handling = ConversationHandling.AGENT;
        agentId = Objects.requireNonNull(memberId);
        agentName = Objects.requireNonNull(memberName);
        handoffRequestedAt = null;
        lastActivityAt = now;
        return true;
    }

    /**
     * Hands the conversation back to the assistant.
     *
     * @param mayOverride whether the member may release a conversation someone else is answering
     */
    public boolean release(UUID memberId, boolean mayOverride, Instant now) {
        if (handling != ConversationHandling.AGENT) {
            return false;
        }
        requireHolderOr(memberId, mayOverride);
        handling = ConversationHandling.ASSISTANT;
        agentId = null;
        agentName = null;
        lastActivityAt = now;
        return true;
    }

    /**
     * Ends the conversation; the visitor can start a new one. Who answered it stays recorded.
     *
     * @param mayOverride whether the member may close a conversation someone else is answering
     */
    public boolean close(UUID memberId, boolean mayOverride, Instant now) {
        if (status == ConversationStatus.CLOSED) {
            return false;
        }
        if (handling == ConversationHandling.AGENT) {
            requireHolderOr(memberId, mayOverride);
        }
        if (handling == ConversationHandling.WAITING) {
            handling = ConversationHandling.ASSISTANT;
            handoffRequestedAt = null;
        }
        status = ConversationStatus.CLOSED;
        lastActivityAt = now;
        return true;
    }

    /**
     * Checks that this member may reply now: only the one who took the conversation over.
     */
    public void acceptAgentReply(UUID memberId, Instant now) {
        requireOpen();
        if (handling != ConversationHandling.AGENT) {
            throw new ConversationNotTakenException();
        }
        requireHolderOr(memberId, false);
        lastActivityAt = now;
    }

    private void requireOpen() {
        if (status != ConversationStatus.OPEN) {
            throw new ConversationClosedException();
        }
    }

    private void requireHolderOr(UUID memberId, boolean mayOverride) {
        if (!agentId.equals(memberId) && !mayOverride) {
            throw new ConversationAssignedException();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    /**
     * @return null for channel conversations
     */
    public byte[] visitorTokenHash() {
        return visitorTokenHash == null ? null : visitorTokenHash.clone();
    }

    public ConversationChannel channel() {
        return channel;
    }

    /**
     * The customer's identity on the channel, e.g. their WhatsApp number; null for web visitors.
     */
    public String contactId() {
        return contactId;
    }

    public String contactName() {
        return contactName;
    }

    /**
     * @return null while neither the customer's browser nor their messages told
     */
    public Language language() {
        return language;
    }

    public ConversationStatus status() {
        return status;
    }

    public int questionCount() {
        return questionCount;
    }

    public String origin() {
        return origin;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    public ConversationHandling handling() {
        return handling;
    }

    public UUID agentId() {
        return agentId;
    }

    public String agentName() {
        return agentName;
    }

    public Instant handoffRequestedAt() {
        return handoffRequestedAt;
    }

    public Instant frustratedAt() {
        return frustratedAt;
    }

    public Set<ConversationIntent> intents() {
        return Collections.unmodifiableSet(intents);
    }

    public boolean intentsEdited() {
        return intentsEdited;
    }
}

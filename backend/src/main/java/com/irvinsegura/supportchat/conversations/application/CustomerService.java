package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationSummary;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationNotFoundException;
import com.irvinsegura.supportchat.conversations.domain.model.CustomerMemory;
import com.irvinsegura.supportchat.conversations.domain.model.CustomerProfile;
import com.irvinsegura.supportchat.conversations.domain.model.NotAChannelCustomerException;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.CustomerRepository;
import com.irvinsegura.supportchat.conversations.domain.port.CustomerRepository.CustomerPage;
import com.irvinsegura.supportchat.conversations.domain.port.CustomerRepository.CustomerSummary;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.identity.OperatorDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers who write through a channel, recognized by their number or address across conversations: the team sees
 * everything they talked about before and keeps notes on them, and the assistant remembers their latest exchanges.
 * <p>
 * The team reaches a customer through one of their conversations, so a phone number or an email address never has to
 * travel in a URL.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final int HISTORY_LIMIT = 20;

    private final ConversationRepository conversations;
    private final CustomerRepository customers;
    private final MessageRepository messages;
    private final OperatorDirectory operators;
    private final Settings settings;
    private final Clock clock;

    CustomerService(ConversationRepository conversations, CustomerRepository customers, MessageRepository messages,
            OperatorDirectory operators, Settings settings, Clock clock) {
        this.conversations = conversations;
        this.customers = customers;
        this.messages = messages;
        this.operators = operators;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * @param enabled  whether the assistant reads customers' earlier conversations at all
     * @param lookBack how far back the assistant remembers
     * @param messages earlier messages the assistant remembers at most
     */
    public record Settings(boolean enabled, Duration lookBack, int messages) {
    }

    @Transactional(readOnly = true)
    public CustomerPageView page(String query, int page, int size) {
        String search = query == null || query.isBlank() ? null : query.strip();
        CustomerPage result = customers.page(search, page, size);
        return new CustomerPageView(result.customers().stream().map(CustomerListItem::of).toList(), page, size,
                result.total());
    }

    /**
     * The customer of a conversation, with everything they talked about before.
     */
    @Transactional(readOnly = true)
    public CustomerView ofConversation(UUID conversationId) {
        Conversation conversation = channelConversation(conversationId);
        return view(conversation.channel(), conversation.contactId());
    }

    /**
     * @param remember null keeps the saved choice
     */
    @Transactional
    public CustomerView edit(UUID conversationId, UUID memberId, String displayName, String notes,
            Boolean remember) {
        Conversation conversation = channelConversation(conversationId);
        CustomerProfile profile = customers.findProfile(conversation.channel(), conversation.contactId())
                .orElseGet(() -> CustomerProfile.blank(conversation.channel(), conversation.contactId()));
        customers.saveProfile(profile.edit(displayName, notes, remember == null ? profile.remember() : remember,
                operators.displayNameOf(memberId),
                clock.instant()));
        return view(conversation.channel(), conversation.contactId());
    }

    /**
     * What the assistant remembers of the customer's earlier conversations, for the prompt of this one; an empty
     * string for web visitors, for customers who asked not to be remembered and when there is nothing recent.
     * Outside a transaction like the rest of the answer, so a failed read cannot spoil the caller's.
     */
    public String memoryOf(UUID conversationId, ConversationChannel channel, String contactId) {
        if (!settings.enabled() || contactId == null || channel == ConversationChannel.WEB) {
            return "";
        }
        try {
            CustomerProfile profile = customers.findProfile(channel, contactId).orElse(null);
            if (profile != null && !profile.remember()) {
                return "";
            }
            Instant since = clock.instant().minus(settings.lookBack());
            String name = profile == null ? null : profile.displayName();
            return CustomerMemory.of(name, messages.earlierOfContact(channel, contactId, conversationId, since,
                    settings.messages()));
        } catch (RuntimeException e) {
            // Answering without the memory beats not answering
            log.warn("Could not load the memory of the customer of conversation {}", conversationId, e);
            return "";
        }
    }

    private CustomerView view(ConversationChannel channel, String contactId) {
        CustomerProfile profile = customers.findProfile(channel, contactId)
                .orElseGet(() -> CustomerProfile.blank(channel, contactId));
        CustomerSummary summary = customers.summaryOf(channel, contactId);
        List<ConversationSummary> history = customers.conversationsOf(channel, contactId, HISTORY_LIMIT).stream()
                .map(ConversationSummary::of)
                .toList();
        return new CustomerView(channel, contactId, summary.contactName(), profile.displayName(), profile.notes(),
                profile.remember(), profile.updatedAt(), profile.updatedByName(), summary.conversations(),
                summary.firstSeenAt(), summary.lastSeenAt(), history);
    }

    private Conversation channelConversation(UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(ConversationNotFoundException::new);
        if (conversation.contactId() == null) {
            throw new NotAChannelCustomerException();
        }
        return conversation;
    }

    /**
     * @param contactName   the name the channel reported last, or null
     * @param displayName   the name the team gave the customer, or null
     * @param notes         the team's notes; never shown to the customer or the model
     * @param remember      whether the assistant reads the customer's earlier conversations
     * @param conversations how many conversations the customer had in all
     * @param history       their latest conversations, most recently active first
     */
    public record CustomerView(ConversationChannel channel, String contactId, @Nullable String contactName,
            @Nullable String displayName, String notes, boolean remember, @Nullable Instant updatedAt,
            @Nullable String updatedByName, long conversations, Instant firstSeenAt, Instant lastSeenAt,
            List<ConversationSummary> history) {
    }

    /**
     * @param latestConversationId where the panel opens the customer
     */
    public record CustomerListItem(ConversationChannel channel, String contactId, @Nullable String contactName,
            @Nullable String displayName, long conversations, Instant firstSeenAt, Instant lastSeenAt,
            UUID latestConversationId) {

        static CustomerListItem of(CustomerSummary summary) {
            return new CustomerListItem(summary.channel(), summary.contactId(), summary.contactName(),
                    summary.displayName(), summary.conversations(), summary.firstSeenAt(), summary.lastSeenAt(),
                    summary.latestConversation());
        }
    }

    public record CustomerPageView(List<CustomerListItem> items, int page, int size, long total) {
    }
}

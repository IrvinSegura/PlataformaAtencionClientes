package com.irvinsegura.supportchat.followups.application;

import com.irvinsegura.supportchat.channels.ReplyNotDelivered;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.CustomerMessages;
import com.irvinsegura.supportchat.conversations.CustomerMessages.Contact;
import com.irvinsegura.supportchat.conversations.CustomerMessages.Recipient;
import com.irvinsegura.supportchat.followups.domain.model.FollowUp;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpConversationNotFoundException;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpNotFoundException;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpStatus;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate.WhatsAppTemplate;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplateNameTakenException;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplateNotFoundException;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpUndeliverableException;
import com.irvinsegura.supportchat.followups.domain.port.FollowUpRepository;
import com.irvinsegura.supportchat.followups.domain.port.FollowUpRepository.FollowUpPage;
import com.irvinsegura.supportchat.followups.domain.port.FollowUpRepository.FollowUpRow;
import com.irvinsegura.supportchat.identity.OperatorDirectory;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Messages the team schedules to write to a customer later, and the templates they write them from. When their moment
 * comes they go out as a reply of the member who scheduled them, through the customer's channel.
 * <p>
 * A follow-up goes to the customer's open conversation when they started a new one since, so the team and the assistant
 * see the follow-up and the answer to it together.
 */
@Service
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    // Follow-ups one workspace sends in one run; the rest wait for the next minute, so no workspace holds up the others
    private static final int BATCH = 50;

    // What a template's {{1}} says when the customer's name is unknown
    private static final String UNKNOWN_NAME = "cliente";

    private final FollowUpRepository followUps;
    private final CustomerMessages customers;
    private final OperatorDirectory operators;
    private final TransactionTemplate transactions;
    private final Clock clock;

    FollowUpService(FollowUpRepository followUps, CustomerMessages customers, OperatorDirectory operators,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.followUps = followUps;
        this.customers = customers;
        this.operators = operators;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FollowUpTemplate> templates() {
        return followUps.templates();
    }

    @Transactional
    public FollowUpTemplate createTemplate(TemplateInput input) {
        Instant now = clock.instant();
        return save(FollowUpTemplate.of(Ids.newId(), input.name(), input.body(), input.whatsapp(), now, now));
    }

    @Transactional
    public FollowUpTemplate updateTemplate(UUID id, TemplateInput input) {
        FollowUpTemplate saved = followUps.findTemplate(id).orElseThrow(FollowUpTemplateNotFoundException::new);
        return save(FollowUpTemplate.of(id, input.name(), input.body(), input.whatsapp(), saved.createdAt(),
                clock.instant()));
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        if (!followUps.deleteTemplate(id)) {
            throw new FollowUpTemplateNotFoundException();
        }
    }

    private FollowUpTemplate save(FollowUpTemplate template) {
        if (!followUps.saveTemplate(template)) {
            throw new FollowUpTemplateNameTakenException();
        }
        return template;
    }

    /**
     * @param templateId the template to write it from, or null to write {@code text}
     */
    @Transactional
    public FollowUpView schedule(UUID conversationId, UUID memberId, UUID templateId, String text, Instant dueAt) {
        Contact contact = customers.contactOf(conversationId)
                .orElseThrow(FollowUpConversationNotFoundException::new);
        FollowUpTemplate template = templateId == null ? null
                : followUps.findTemplate(templateId).orElseThrow(FollowUpTemplateNotFoundException::new);
        FollowUp followUp = FollowUp.schedule(Ids.newId(), contact.conversationId(), contact.channel(), template, text,
                dueAt, memberId, operators.displayNameOf(memberId), clock.instant());
        FollowUpRow row = new FollowUpRow(followUp, template == null ? null : template.name(), contact.channel(),
                contact.contactId(), contact.contactName());
        followUps.insert(row);
        return FollowUpView.of(row);
    }

    @Transactional
    public FollowUpView cancel(UUID followUpId) {
        FollowUp followUp = followUps.findForUpdate(followUpId).orElseThrow(FollowUpNotFoundException::new);
        followUp.cancel(clock.instant());
        followUps.update(followUp);
        return followUps.ofConversation(followUp.conversationId()).stream()
                .filter(row -> row.followUp().id().equals(followUpId))
                .findFirst()
                .map(FollowUpView::of)
                .orElseThrow(FollowUpNotFoundException::new);
    }

    /**
     * The channel could not deliver a message after it was saved: if a follow-up went out as it, it shows as failed
     * instead of sent. Any other message is ignored.
     */
    @Transactional
    public void notDelivered(UUID messageId, ReplyNotDelivered.Reason reason) {
        String code = switch (reason) {
            case CHANNEL_UNAVAILABLE -> FollowUpUndeliverableException.CHANNEL_UNAVAILABLE;
            case REFUSED -> FollowUpUndeliverableException.DELIVERY_REFUSED;
        };
        followUps.findByMessageForUpdate(messageId)
                .filter(followUp -> followUp.notDelivered(code, clock.instant()))
                .ifPresent(followUps::update);
    }

    @Transactional(readOnly = true)
    public List<FollowUpView> ofConversation(UUID conversationId) {
        if (customers.contactOf(conversationId).isEmpty()) {
            throw new FollowUpConversationNotFoundException();
        }
        return followUps.ofConversation(conversationId).stream().map(FollowUpView::of).toList();
    }

    @Transactional(readOnly = true)
    public FollowUpPageView page(FollowUpStatus status, int page, int size) {
        FollowUpPage result = followUps.page(status, page, size);
        return new FollowUpPageView(result.followUps().stream().map(FollowUpView::of).toList(), page, size,
                result.total());
    }

    /**
     * Sends every follow-up whose moment came, in every workspace. Each one is sent in its own transaction, so one
     * that fails does not hold back the rest, and two instances never send the same one.
     *
     * @return how many follow-ups were handled (sent or failed)
     */
    public int sendDue() {
        Instant now = clock.instant();
        int handled = 0;
        List<UUID> workspaces = transactions.execute(status -> followUps.workspacesWithDue(now));
        for (UUID workspaceId : workspaces == null ? List.<UUID>of() : workspaces) {
            try {
                handled += TenantContext.callAs(workspaceId, () -> sendDueOfWorkspace(now));
            } catch (RuntimeException e) {
                log.warn("Could not send the follow-ups of workspace {}", workspaceId, e);
            }
        }
        return handled;
    }

    /**
     * Sends the follow-ups of one workspace whose moment came, up to one batch.
     *
     * @return how many follow-ups were handled (sent or failed)
     */
    public int sendDue(UUID workspaceId) {
        return TenantContext.callAs(workspaceId, () -> sendDueOfWorkspace(clock.instant()));
    }

    private int sendDueOfWorkspace(Instant now) {
        int handled = 0;
        while (handled < BATCH) {
            AtomicReference<UUID> claimed = new AtomicReference<>();
            try {
                if (!Boolean.TRUE.equals(transactions.execute(status -> sendNext(now, claimed)))) {
                    break;
                }
            } catch (RuntimeException e) {
                if (claimed.get() == null) {
                    throw e;
                }
                // Its transaction rolled back, so it is still scheduled and would be claimed first again forever
                log.warn("Could not send follow-up {}", claimed.get(), e);
                transactions.executeWithoutResult(status -> markFailed(claimed.get(), now));
            }
            handled++;
        }
        return handled;
    }

    private boolean sendNext(Instant now, AtomicReference<UUID> claimedId) {
        Optional<FollowUp> claimed = followUps.claimDue(now);
        if (claimed.isEmpty()) {
            return false;
        }
        FollowUp followUp = claimed.get();
        claimedId.set(followUp.id());
        try {
            Recipient recipient = customers.recipientOf(followUp.conversationId())
                    .orElseThrow(() -> new FollowUpUndeliverableException(
                            FollowUpUndeliverableException.CONVERSATION_GONE));
            Optional<WhatsAppTemplate> whatsapp = followUp.route(recipient.channel(), recipient.lastMessageAt(), now);
            String name = recipient.customerName();
            String text = followUp.textFor(name);
            if (text.isEmpty()) {
                throw new FollowUpUndeliverableException(FollowUpUndeliverableException.NO_TEXT);
            }
            UUID messageId = customers.sendAsMember(recipient.conversationId(), followUp.createdBy(),
                    followUp.createdByName(), text, whatsapp.map(template -> reply(template, name)).orElse(null), now);
            followUp.sent(recipient.conversationId(), messageId, now);
        } catch (FollowUpUndeliverableException e) {
            followUp.failed(e.code(), now);
        }
        followUps.update(followUp);
        return true;
    }

    private void markFailed(UUID followUpId, Instant now) {
        followUps.findForUpdate(followUpId)
                .filter(followUp -> followUp.status() == FollowUpStatus.SCHEDULED)
                .ifPresent(followUp -> {
                    followUp.failed(FollowUpUndeliverableException.SEND_ERROR, now);
                    followUps.update(followUp);
                });
    }

    private static ConversationReply.Template reply(WhatsAppTemplate template, String customerName) {
        String name = customerName == null || customerName.isBlank() ? UNKNOWN_NAME : customerName;
        return new ConversationReply.Template(template.name(), template.language(),
                template.nameParameter() ? List.of(name) : List.of());
    }

    /**
     * @param whatsapp the approved WhatsApp template sent after 24 hours of silence, or null
     */
    public record TemplateInput(String name, String body, WhatsAppTemplate whatsapp) {
    }

    /**
     * @param templateName     the template it was written from, or null (also once that template is deleted)
     * @param text             what the customer reads, with {@code {nombre}} still in place
     * @param whatsappTemplate the approved WhatsApp template it goes out as after 24 hours of silence, or null
     * @param failure          why it could not be sent, as a stable code; null unless it failed
     * @param sentConversationId the conversation it went out in, which may be a newer one of the same customer
     */
    public record FollowUpView(UUID id, UUID conversationId, @Nullable UUID templateId, @Nullable String templateName,
            String text, @Nullable String whatsappTemplate, Instant dueAt, FollowUpStatus status,
            @Nullable String failure, String createdByName, Instant createdAt, @Nullable Instant finishedAt,
            @Nullable UUID sentConversationId, ConversationChannel channel, @Nullable String contactId,
            @Nullable String contactName) {

        static FollowUpView of(FollowUpRow row) {
            FollowUp followUp = row.followUp();
            return new FollowUpView(followUp.id(), followUp.conversationId(), followUp.templateId(),
                    row.templateName(), followUp.body(),
                    followUp.whatsapp() == null ? null : followUp.whatsapp().name(), followUp.dueAt(),
                    followUp.status(), followUp.failure(), followUp.createdByName(), followUp.createdAt(),
                    followUp.finishedAt(), followUp.sentConversationId(), row.channel(), row.contactId(),
                    row.contactName());
        }
    }

    public record FollowUpPageView(List<FollowUpView> items, int page, int size, long total) {
    }
}

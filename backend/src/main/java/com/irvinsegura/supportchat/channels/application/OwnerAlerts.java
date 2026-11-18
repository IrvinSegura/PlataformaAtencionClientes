package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.billing.UsageLimitApproaching;
import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.TemplateMessage;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppChannelRepository;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender.WhatsAppRejectedException;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender.WhatsAppUnavailableException;
import com.irvinsegura.supportchat.conversations.AttentionAlerts;
import com.irvinsegura.supportchat.conversations.AttentionAlerts.Delivery;
import com.irvinsegura.supportchat.conversations.AttentionReason;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.CustomerNeedsAttention;
import com.irvinsegura.supportchat.conversations.DifficultQuestionRepeated;
import com.irvinsegura.supportchat.conversations.OwnerAlertRecipients;
import com.irvinsegura.supportchat.conversations.OwnerAlertRecipients.Recipients;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.i18n.LocalizedTexts;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells the owner by WhatsApp, from the business's own number, that a customer needs a person, that a difficult
 * question keeps coming back or that the plan nears its monthly limit, with a link to the panel. Without an approved
 * template WhatsApp only delivers to numbers that wrote to the business in the last 24 hours; with one, it always
 * does.
 * <p>
 * Each recipient is tried once. A customer's alert records the outcome, so a failure shows in the panel instead of
 * being retried into a duplicate for the numbers that did receive it; the others only log it.
 */
@Component
public class OwnerAlerts {

    private static final Logger log = LoggerFactory.getLogger(OwnerAlerts.class);
    static final String DIRECTION_OUTBOUND = WhatsAppConversations.DIRECTION_OUTBOUND;
    // Owners use the panel in Spanish, so the alerts exist only in Spanish
    private static final LocalizedTexts TEXTS = LocalizedTexts.load("owner-alerts");

    private final WhatsAppChannelRepository channels;
    private final WhatsAppSender sender;
    private final AttentionAlerts alerts;
    private final OwnerAlertRecipients recipients;
    private final UsageMeter usage;
    private final AlertSettings settings;
    private final TransactionTemplate transactions;
    private final Clock clock;

    OwnerAlerts(WhatsAppChannelRepository channels, WhatsAppSender sender, AttentionAlerts alerts,
            OwnerAlertRecipients recipients, UsageMeter usage, AlertSettings settings,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.channels = channels;
        this.sender = sender;
        this.alerts = alerts;
        this.recipients = recipients;
        this.usage = usage;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener
    void on(CustomerNeedsAttention alert) {
        TenantContext.runAs(alert.workspaceId(), () -> deliver(alert));
    }

    /**
     * Unlike a customer's alert, nothing waits on the panel for its outcome: a question that keeps coming back stays
     * at the top of the difficult questions, alert or not.
     */
    @Async
    @TransactionalEventListener
    void on(DifficultQuestionRepeated alert) {
        TenantContext.runAs(alert.workspaceId(), () -> deliver(alert));
    }

    private void deliver(DifficultQuestionRepeated alert) {
        deliverToEach("difficult question " + alert.questionId(), new Recipients(alert.recipients(),
                        alert.template(), alert.templateLanguage()),
                List.of(text("difficult.who"), difficultWhat(alert), difficultLink()), text(alert));
    }

    /**
     * The usage that reached the share is already saved; whom to tell is read now, from the workspace's settings.
     */
    @Async
    @TransactionalEventListener
    void on(UsageLimitApproaching alert) {
        TenantContext.runAs(alert.workspaceId(), () -> {
            Recipients wanted = transactions.execute(status -> recipients.usageLimit());
            if (wanted == null || wanted.phones().isEmpty()) {
                return;
            }
            deliverToEach(alert.percent() + "% usage alert of " + alert.metric(), wanted,
                    List.of(text("usage.who", alert.planName()), usageWhat(alert), usageLink()), text(alert));
        });
    }

    /**
     * Sends one alert that nothing records to each recipient, logging the ones that did not arrive.
     */
    private void deliverToEach(String what, Recipients to, List<String> variables, String text) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        Optional<WhatsAppChannel> channel = transactions.execute(status -> channels.find())
                .filter(WhatsAppChannel::enabled);
        if (channel.isEmpty()) {
            log.info("The {} of workspace {} was not sent: no WhatsApp number to send from", what, workspaceId);
            return;
        }
        for (String recipient : to.phones()) {
            Outcome outcome = send(channel.get(), what, recipient, to.template(), to.templateLanguage(), variables,
                    text);
            if (outcome.delivery() != Delivery.SENT) {
                log.warn("The {} of workspace {} did not reach a number: {}", what, workspaceId, outcome.detail());
            }
        }
    }

    private void deliver(CustomerNeedsAttention alert) {
        Optional<WhatsAppChannel> channel = transactions.execute(status -> channels.find())
                .filter(WhatsAppChannel::enabled);
        for (String recipient : alert.recipients()) {
            if (channel.isEmpty()) {
                alerts.recordDelivery(alert.alertId(), recipient, Delivery.NO_CHANNEL, text("delivery.no-channel"));
                continue;
            }
            Outcome outcome = send(channel.get(), "alert " + alert.alertId(), recipient, alert.template(),
                    alert.templateLanguage(), List.of(customer(alert), what(alert.reason()), link(alert)),
                    text(alert));
            alerts.recordDelivery(alert.alertId(), recipient, outcome.delivery(), outcome.detail());
        }
    }

    /**
     * Sends the template with its three variables when there is one, the text otherwise.
     *
     * @param what names the alert in the logs
     */
    private Outcome send(WhatsAppChannel channel, String what, String recipient, String template,
            String templateLanguage, List<String> variables, String text) {
        try {
            if (template != null) {
                sender.sendTemplate(channel, new TemplateMessage(recipient, template, templateLanguage, variables));
            } else {
                sender.send(channel, OutboundMessage.text(recipient, text));
            }
        } catch (WhatsAppRejectedException e) {
            log.warn("WhatsApp refused the {} of workspace {}: {}", what, channel.workspaceId(), e.getMessage());
            return new Outcome(Delivery.REJECTED,
                    text(template != null ? "delivery.template-refused" : "delivery.outside-window"));
        } catch (WhatsAppUnavailableException e) {
            log.warn("WhatsApp unavailable for the {} of workspace {}", what, channel.workspaceId(), e);
            return new Outcome(Delivery.FAILED, text("delivery.unavailable"));
        }
        transactions.executeWithoutResult(status ->
                usage.record(clock.instant(), UsageMetric.WHATSAPP_MESSAGES, DIRECTION_OUTBOUND, 1));
        return new Outcome(Delivery.SENT, null);
    }

    String text(CustomerNeedsAttention alert) {
        StringBuilder text = new StringBuilder(text("attention.text", customer(alert), what(alert.reason())));
        if (alert.excerpt() != null) {
            text.append(text("attention.excerpt", alert.excerpt()));
        }
        return text.append(text("attention.link", link(alert))).toString();
    }

    String text(DifficultQuestionRepeated alert) {
        return text("difficult.text", alert.occurrences(), alert.question(), difficultLink());
    }

    // The three variables of the same template customers' alerts use: who, what happened and where to see it.
    // WhatsApp refuses line breaks inside a template variable
    static String difficultWhat(DifficultQuestionRepeated alert) {
        return text("difficult.what", alert.occurrences(), alert.question().replaceAll("\\s+", " "));
    }

    private String difficultLink() {
        return settings.panelUrl().replaceAll("/+$", "") + "/knowledge/difficult";
    }

    String text(UsageLimitApproaching alert) {
        return text(alert.extra() > 0 ? "usage.text-with-top-ups" : "usage.text", alert.percent(), alert.used(),
                alert.included(), usageName(alert.metric()), alert.planName())
                + (alert.billedPastLimit()
                        ? text("usage.billed", usageLink())
                        : text("usage.stops", text("usage.stops." + alert.metric().name()), usageLink()));
    }

    static String usageWhat(UsageLimitApproaching alert) {
        return text("usage.what", alert.percent(), usageName(alert.metric()), alert.used(), alert.included());
    }

    static String usageName(UsageMetric metric) {
        return text("usage.name." + metric.name());
    }

    private String usageLink() {
        return settings.panelUrl().replaceAll("/+$", "") + "/billing";
    }

    static String customer(CustomerNeedsAttention alert) {
        String name = alert.contactName() == null || alert.contactName().isBlank()
                ? text("attention.unnamed")
                : alert.contactName();
        return switch (alert.channel()) {
            case WEB -> text("attention.web-visitor");
            case WHATSAPP -> text("attention.whatsapp", name, alert.contactId());
            case EMAIL -> text("attention.email", name, alert.contactId());
            case MESSENGER, INSTAGRAM -> text("attention.meta", name, label(alert.channel()));
        };
    }

    static String what(AttentionReason reason) {
        return text("attention.reason." + reason.name());
    }

    private static String label(ConversationChannel channel) {
        return channel == ConversationChannel.MESSENGER ? "Messenger" : "Instagram";
    }

    private String link(CustomerNeedsAttention alert) {
        return settings.panelUrl().replaceAll("/+$", "") + "/conversations/" + alert.conversationId();
    }

    private static String text(String key, Object... arguments) {
        return TEXTS.text(Language.ES, key, arguments);
    }

    private record Outcome(Delivery delivery, String detail) {
    }

    /**
     * @param panelUrl the operator panel, e.g. {@code https://panel.example.com}
     */
    public record AlertSettings(String panelUrl) {
    }
}

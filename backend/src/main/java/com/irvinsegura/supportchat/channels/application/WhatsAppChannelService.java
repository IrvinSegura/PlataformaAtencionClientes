package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.billing.UsageAllowance;
import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.channels.application.WhatsAppPayload.Delivered;
import com.irvinsegura.supportchat.channels.domain.model.InvalidWebhookSignatureException;
import com.irvinsegura.supportchat.channels.domain.model.WebhookSignature;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel.Settings;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannelNotFoundException;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppChannelRepository;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Connects a workspace's WhatsApp number and takes Meta's webhook deliveries for it.
 */
@Service
public class WhatsAppChannelService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelService.class);
    static final String DIRECTION_INBOUND = "inbound";

    private final WhatsAppChannelRepository channels;
    private final UsageMeter usage;
    private final UsageAllowance allowance;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    WhatsAppChannelService(WhatsAppChannelRepository channels, UsageMeter usage, UsageAllowance allowance,
            ApplicationEventPublisher events, JsonMapper json, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.channels = channels;
        this.usage = usage;
        this.allowance = allowance;
        this.events = events;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Optional<WhatsAppChannelView> view() {
        return transactions.execute(status -> channels.find().map(WhatsAppChannelView::of));
    }

    public WhatsAppChannelView configure(Settings settings) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            WhatsAppChannel channel = channels.find()
                    .map(existing -> {
                        existing.change(settings, now);
                        return existing;
                    })
                    .orElseGet(() -> WhatsAppChannel.connect(TenantContext.requireWorkspaceId(),
                            SecretTokens.generate(), SecretTokens.generate(), settings, now));
            channels.save(channel);
            log.info("WhatsApp number {} saved for workspace {}, enabled: {}", channel.phoneNumberId(),
                    channel.workspaceId(), channel.enabled());
            return WhatsAppChannelView.of(channel);
        });
    }

    public void disconnect() {
        transactions.executeWithoutResult(status -> {
            channels.find().orElseThrow(WhatsAppChannelNotFoundException::new);
            channels.delete(TenantContext.requireWorkspaceId());
        });
    }

    /**
     * Meta's subscription check: it sends the verify token and expects the challenge back.
     *
     * @return the challenge to answer with
     */
    public String verifySubscription(String channelKey, String mode, String verifyToken, String challenge) {
        UUID workspaceId = workspaceOf(channelKey);
        WhatsAppChannel channel = TenantContext.callAs(workspaceId,
                () -> transactions.execute(status -> channels.find()))
                .orElseThrow(WhatsAppChannelNotFoundException::new);
        if (!"subscribe".equals(mode) || verifyToken == null || challenge == null
                || !MessageDigest.isEqual(channel.verifyToken().getBytes(StandardCharsets.UTF_8),
                        verifyToken.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException();
        }
        return challenge;
    }

    /**
     * A webhook delivery: checks Meta's signature over the exact bytes received, keeps each new message once and
     * hands it to be answered after this returns.
     */
    public void receive(String channelKey, byte[] body, String signature) {
        UUID workspaceId = workspaceOf(channelKey);
        TenantContext.runAs(workspaceId, () -> transactions.executeWithoutResult(status -> {
            WhatsAppChannel channel = channels.find().orElseThrow(WhatsAppChannelNotFoundException::new);
            if (!WebhookSignature.matches(channel.appSecret(), body, signature)) {
                throw new InvalidWebhookSignatureException();
            }
            if (!channel.enabled()) {
                // Acknowledged so Meta stops delivering it; a paused number simply goes unanswered
                return;
            }
            Instant now = clock.instant();
            for (Delivered delivered : WhatsAppPayload.read(parse(body))) {
                if (!channel.receivesFor(delivered.phoneNumberId())
                        || !channels.firstDelivery(workspaceId, delivered.message().messageId(), now)) {
                    continue;
                }
                if (!allowance.allows(UsageMetric.WHATSAPP_MESSAGES)) {
                    // The plan's WhatsApp messages for the month are used up; the panel says so
                    log.info("Ignored a WhatsApp message to workspace {}: its plan's messages are used up",
                            workspaceId);
                    continue;
                }
                usage.record(now, UsageMetric.WHATSAPP_MESSAGES, DIRECTION_INBOUND, 1);
                events.publishEvent(new WhatsAppMessageReceived(workspaceId, delivered.message().from(),
                        delivered.message().contactName(), delivered.message().text(),
                        delivered.message().buttonId(), channel.model()));
            }
        }));
    }

    private UUID workspaceOf(String channelKey) {
        return transactions.execute(status -> channels.workspaceOfKey(channelKey))
                .orElseThrow(WhatsAppChannelNotFoundException::new);
    }

    private JsonNode parse(byte[] body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            // Signed by Meta but unreadable: nothing to answer, and a retry would not help
            log.warn("Ignored an unreadable WhatsApp webhook delivery");
            return json.createObjectNode();
        }
    }

    /**
     * The connection as the panel shows it; the access token and the app secret are never sent back.
     *
     * @param webhookPath the path to append to the API's public address in Meta's webhook settings
     * @param verifyToken the token to paste in Meta's webhook settings
     */
    public record WhatsAppChannelView(String phoneNumberId, @Nullable String displayPhoneNumber, boolean enabled,
            String webhookPath, String verifyToken, ModelPreference model, Instant updatedAt) {

        static WhatsAppChannelView of(WhatsAppChannel channel) {
            return new WhatsAppChannelView(channel.phoneNumberId(), channel.displayPhoneNumber(), channel.enabled(),
                    "/api/v1/channels/whatsapp/" + channel.channelKey() + "/webhook", channel.verifyToken(),
                    channel.model(), channel.updatedAt());
        }
    }
}

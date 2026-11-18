package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.application.MetaPayload.Delivered;
import com.irvinsegura.supportchat.channels.domain.model.InvalidWebhookSignatureException;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannel.Settings;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannelNotFoundException;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import com.irvinsegura.supportchat.channels.domain.model.WebhookSignature;
import com.irvinsegura.supportchat.channels.domain.port.InboundMessageLog;
import com.irvinsegura.supportchat.channels.domain.port.MetaChannelRepository;
import com.irvinsegura.supportchat.channels.domain.port.MetaChannelRepository.KeyOwner;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
 * Connects a workspace's Facebook Page (Messenger) and Instagram account, and takes Meta's webhook deliveries for
 * them.
 */
@Service
public class MetaChannelService {

    private static final Logger log = LoggerFactory.getLogger(MetaChannelService.class);

    private final MetaChannelRepository channels;
    private final InboundMessageLog received;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    MetaChannelService(MetaChannelRepository channels, InboundMessageLog received, ApplicationEventPublisher events,
            JsonMapper json, PlatformTransactionManager transactionManager, Clock clock) {
        this.channels = channels;
        this.received = received;
        this.events = events;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Optional<MetaChannelView> view(MetaPlatform platform) {
        return transactions.execute(status -> channels.find(platform).map(MetaChannelView::of));
    }

    public MetaChannelView configure(MetaPlatform platform, Settings settings) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            MetaChannel channel = channels.find(platform)
                    .map(existing -> {
                        existing.change(settings, now);
                        return existing;
                    })
                    .orElseGet(() -> MetaChannel.connect(TenantContext.requireWorkspaceId(), platform,
                            SecretTokens.generate(), SecretTokens.generate(), settings, now));
            channels.save(channel);
            log.info("{} account {} saved for workspace {}, enabled: {}", platform, channel.accountId(),
                    channel.workspaceId(), channel.enabled());
            return MetaChannelView.of(channel);
        });
    }

    public void disconnect(MetaPlatform platform) {
        transactions.executeWithoutResult(status -> {
            channels.find(platform).orElseThrow(MetaChannelNotFoundException::new);
            channels.delete(TenantContext.requireWorkspaceId(), platform);
        });
    }

    /**
     * Meta's subscription check: it sends the verify token and expects the challenge back.
     *
     * @return the challenge to answer with
     */
    public String verifySubscription(MetaPlatform platform, String channelKey, String mode, String verifyToken,
            String challenge) {
        KeyOwner owner = ownerOf(platform, channelKey);
        MetaChannel channel = TenantContext.callAs(owner.workspaceId(),
                () -> transactions.execute(status -> channels.find(platform)))
                .orElseThrow(MetaChannelNotFoundException::new);
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
    public void receive(MetaPlatform platform, String channelKey, byte[] body, String signature) {
        KeyOwner owner = ownerOf(platform, channelKey);
        TenantContext.runAs(owner.workspaceId(), () -> transactions.executeWithoutResult(status -> {
            MetaChannel channel = channels.find(platform).orElseThrow(MetaChannelNotFoundException::new);
            if (!WebhookSignature.matches(channel.appSecret(), body, signature)) {
                throw new InvalidWebhookSignatureException();
            }
            if (!channel.enabled()) {
                // Acknowledged so Meta stops delivering it; a paused account simply goes unanswered
                return;
            }
            Instant now = clock.instant();
            String conversationChannel = MetaConversations.channelOf(platform).name();
            for (Delivered delivered : MetaPayload.read(parse(body), platform)) {
                if (channel.receivesFor(delivered.accountId()) && received.firstDelivery(owner.workspaceId(),
                        conversationChannel, delivered.messageId(), now)) {
                    events.publishEvent(new MetaMessageReceived(owner.workspaceId(), platform, delivered.senderId(),
                            delivered.text(), delivered.payload()));
                }
            }
        }));
    }

    private KeyOwner ownerOf(MetaPlatform platform, String channelKey) {
        return transactions.execute(status -> channels.ownerOfKey(channelKey))
                .filter(owner -> owner.platform() == platform)
                .orElseThrow(MetaChannelNotFoundException::new);
    }

    private JsonNode parse(byte[] body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            // Signed by Meta but unreadable: nothing to answer, and a retry would not help
            log.warn("Ignored an unreadable Messenger Platform webhook delivery");
            return json.createObjectNode();
        }
    }

    /**
     * The connection as the panel shows it; the access token and the app secret are never sent back.
     *
     * @param webhookPath the path to append to the API's public address in the Meta app's webhook settings
     * @param verifyToken the token to paste in the Meta app's webhook settings
     */
    public record MetaChannelView(MetaPlatform platform, String accountId, @Nullable String displayName,
            boolean enabled, String webhookPath, String verifyToken, Instant updatedAt) {

        static MetaChannelView of(MetaChannel channel) {
            return new MetaChannelView(channel.platform(), channel.accountId(), channel.displayName(),
                    channel.enabled(),
                    "/api/v1/channels/" + channel.platform().pathName() + "/" + channel.channelKey() + "/webhook",
                    channel.verifyToken(), channel.updatedAt());
        }
    }
}

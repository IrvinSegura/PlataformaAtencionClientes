package com.irvinsegura.supportchat.channels.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A workspace's Facebook Page (Messenger) or Instagram professional account, connected through a Meta app.
 * <p>
 * As with WhatsApp, the channel key names the workspace in the public webhook URL, the verify token answers Meta's
 * subscription check, the app secret verifies each delivery and the Page access token sends the replies. The last two
 * come from Meta and are never shown again once saved.
 */
public final class MetaChannel {

    private static final Pattern ACCOUNT_ID = Pattern.compile("\\d{5,30}");
    private static final int MAX_DISPLAY_NAME = 100;
    private static final int MAX_ACCESS_TOKEN = 2048;
    private static final int MAX_APP_SECRET = 256;

    private final UUID workspaceId;
    private final MetaPlatform platform;
    private final String channelKey;
    private final String verifyToken;
    private final Instant createdAt;
    private String accountId;
    private String displayName;
    private String accessToken;
    private String appSecret;
    private boolean enabled;
    private Instant updatedAt;

    private MetaChannel(UUID workspaceId, MetaPlatform platform, String channelKey, String verifyToken,
            String accountId, String displayName, String accessToken, String appSecret, boolean enabled,
            Instant createdAt, Instant updatedAt) {
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.platform = Objects.requireNonNull(platform);
        this.channelKey = Objects.requireNonNull(channelKey);
        this.verifyToken = Objects.requireNonNull(verifyToken);
        this.accountId = accountId;
        this.displayName = displayName;
        this.accessToken = accessToken;
        this.appSecret = appSecret;
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * @param channelKey  a new random key for the webhook URL
     * @param verifyToken a new random token for Meta's subscription check
     */
    public static MetaChannel connect(UUID workspaceId, MetaPlatform platform, String channelKey, String verifyToken,
            Settings settings, Instant now) {
        if (blank(settings.accessToken()) || blank(settings.appSecret())) {
            throw new InvalidMetaChannelException("The access token and the app secret are required");
        }
        MetaChannel channel = new MetaChannel(workspaceId, platform, channelKey, verifyToken, null, null, null, null,
                false, now, now);
        channel.change(settings, now);
        return channel;
    }

    public static MetaChannel restore(UUID workspaceId, MetaPlatform platform, String channelKey, String verifyToken,
            String accountId, String displayName, String accessToken, String appSecret, boolean enabled,
            Instant createdAt, Instant updatedAt) {
        return new MetaChannel(workspaceId, platform, channelKey, verifyToken, accountId, displayName, accessToken,
                appSecret, enabled, createdAt, updatedAt);
    }

    /**
     * Blank secrets keep the saved ones, so the panel never needs to read them back.
     */
    public void change(Settings settings, Instant now) {
        String account = settings.accountId() == null ? "" : settings.accountId().strip();
        if (!ACCOUNT_ID.matcher(account).matches()) {
            throw new InvalidMetaChannelException(platform == MetaPlatform.MESSENGER
                    ? "The Page ID is the number Meta shows in the Page's settings, digits only"
                    : "The Instagram account ID is the number Meta shows for the professional account, digits only");
        }
        String name = blank(settings.displayName()) ? null : settings.displayName().strip();
        if (name != null && name.length() > MAX_DISPLAY_NAME) {
            throw new InvalidMetaChannelException("The name is too long");
        }
        if (!blank(settings.accessToken()) && settings.accessToken().strip().length() > MAX_ACCESS_TOKEN) {
            throw new InvalidMetaChannelException("The access token is too long");
        }
        if (!blank(settings.appSecret()) && settings.appSecret().strip().length() > MAX_APP_SECRET) {
            throw new InvalidMetaChannelException("The app secret is too long");
        }
        accountId = account;
        displayName = name;
        if (!blank(settings.accessToken())) {
            accessToken = settings.accessToken().strip();
        }
        if (!blank(settings.appSecret())) {
            appSecret = settings.appSecret().strip();
        }
        enabled = settings.enabled();
        updatedAt = now;
    }

    /**
     * Whether a webhook entry is for this account; a Meta app delivers every Page's messages to each subscribed URL.
     */
    public boolean receivesFor(String accountId) {
        return this.accountId.equals(accountId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public MetaPlatform platform() {
        return platform;
    }

    public String channelKey() {
        return channelKey;
    }

    public String verifyToken() {
        return verifyToken;
    }

    public String accountId() {
        return accountId;
    }

    public String displayName() {
        return displayName;
    }

    public String accessToken() {
        return accessToken;
    }

    public String appSecret() {
        return appSecret;
    }

    public boolean enabled() {
        return enabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * @param accountId   the Page ID (Messenger) or the Instagram professional account ID
     * @param accessToken the Page access token; blank keeps the saved one
     * @param appSecret   blank keeps the saved one
     */
    public record Settings(String accountId, String displayName, String accessToken, String appSecret,
            boolean enabled) {

        @Override
        public String toString() {
            // Secrets stay out of logs
            return "Settings[accountId=" + accountId + ", enabled=" + enabled + "]";
        }
    }
}

package com.irvinsegura.supportchat.channels.domain.model;

import com.irvinsegura.supportchat.conversations.ModelPreference;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A workspace's WhatsApp Business number, connected through Meta's Cloud API.
 * <p>
 * The channel key names the workspace in the public webhook URL; the verify token answers Meta's subscription check;
 * the app secret verifies that each delivery comes from Meta; the access token sends replies as the business. The
 * last two come from Meta and are never shown again once saved.
 */
public final class WhatsAppChannel {

    private static final Pattern PHONE_NUMBER_ID = Pattern.compile("\\d{5,30}");
    private static final int MAX_DISPLAY_NUMBER = 32;
    private static final int MAX_ACCESS_TOKEN = 2048;
    private static final int MAX_APP_SECRET = 256;

    private final UUID workspaceId;
    private final String channelKey;
    private final String verifyToken;
    private final Instant createdAt;
    private String phoneNumberId;
    private String displayPhoneNumber;
    private String accessToken;
    private String appSecret;
    private boolean enabled;
    private ModelPreference model;
    private Instant updatedAt;

    private WhatsAppChannel(UUID workspaceId, String channelKey, String verifyToken, String phoneNumberId,
            String displayPhoneNumber, String accessToken, String appSecret, boolean enabled, ModelPreference model,
            Instant createdAt, Instant updatedAt) {
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.channelKey = Objects.requireNonNull(channelKey);
        this.verifyToken = Objects.requireNonNull(verifyToken);
        this.phoneNumberId = phoneNumberId;
        this.displayPhoneNumber = displayPhoneNumber;
        this.accessToken = accessToken;
        this.appSecret = appSecret;
        this.enabled = enabled;
        this.model = model == null ? ModelPreference.AUTOMATIC : model;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * @param channelKey  a new random key for the webhook URL
     * @param verifyToken a new random token for Meta's subscription check
     */
    public static WhatsAppChannel connect(UUID workspaceId, String channelKey, String verifyToken, Settings settings,
            Instant now) {
        if (blank(settings.accessToken()) || blank(settings.appSecret())) {
            throw new InvalidWhatsAppChannelException("The access token and the app secret are required");
        }
        WhatsAppChannel channel = new WhatsAppChannel(workspaceId, channelKey, verifyToken, null, null, null, null,
                false, ModelPreference.AUTOMATIC, now, now);
        channel.change(settings, now);
        return channel;
    }

    public static WhatsAppChannel restore(UUID workspaceId, String channelKey, String verifyToken,
            String phoneNumberId, String displayPhoneNumber, String accessToken, String appSecret, boolean enabled,
            ModelPreference model, Instant createdAt, Instant updatedAt) {
        return new WhatsAppChannel(workspaceId, channelKey, verifyToken, phoneNumberId, displayPhoneNumber,
                accessToken, appSecret, enabled, model, createdAt, updatedAt);
    }

    /**
     * Blank secrets keep the saved ones, so the panel never needs to read them back.
     */
    public void change(Settings settings, Instant now) {
        String number = settings.phoneNumberId() == null ? "" : settings.phoneNumberId().strip();
        if (!PHONE_NUMBER_ID.matcher(number).matches()) {
            throw new InvalidWhatsAppChannelException("The phone number id is the number Meta shows in the API "
                    + "setup, digits only");
        }
        String display = blank(settings.displayPhoneNumber()) ? null : settings.displayPhoneNumber().strip();
        if (display != null && display.length() > MAX_DISPLAY_NUMBER) {
            throw new InvalidWhatsAppChannelException("The phone number is too long");
        }
        if (!blank(settings.accessToken()) && settings.accessToken().strip().length() > MAX_ACCESS_TOKEN) {
            throw new InvalidWhatsAppChannelException("The access token is too long");
        }
        if (!blank(settings.appSecret()) && settings.appSecret().strip().length() > MAX_APP_SECRET) {
            throw new InvalidWhatsAppChannelException("The app secret is too long");
        }
        phoneNumberId = number;
        displayPhoneNumber = display;
        if (!blank(settings.accessToken())) {
            accessToken = settings.accessToken().strip();
        }
        if (!blank(settings.appSecret())) {
            appSecret = settings.appSecret().strip();
        }
        enabled = settings.enabled();
        // Left out by older clients: the saved choice stays
        if (settings.model() != null) {
            model = settings.model();
        }
        updatedAt = now;
    }

    /**
     * Whether a webhook delivery is for this number; Meta sends one app's deliveries for all its numbers to every
     * subscribed URL.
     */
    public boolean receivesFor(String phoneNumberId) {
        return this.phoneNumberId.equals(phoneNumberId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public String channelKey() {
        return channelKey;
    }

    public String verifyToken() {
        return verifyToken;
    }

    public String phoneNumberId() {
        return phoneNumberId;
    }

    public String displayPhoneNumber() {
        return displayPhoneNumber;
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

    /**
     * The model that answers this number's customers.
     */
    public ModelPreference model() {
        return model;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * @param accessToken blank keeps the saved one
     * @param appSecret   blank keeps the saved one
     * @param model       null keeps the saved choice
     */
    public record Settings(String phoneNumberId, String displayPhoneNumber, String accessToken, String appSecret,
            boolean enabled, ModelPreference model) {

        public Settings(String phoneNumberId, String displayPhoneNumber, String accessToken, String appSecret,
                boolean enabled) {
            this(phoneNumberId, displayPhoneNumber, accessToken, appSecret, enabled, null);
        }

        @Override
        public String toString() {
            // Secrets stay out of logs
            return "Settings[phoneNumberId=" + phoneNumberId + ", enabled=" + enabled + ", model=" + model + "]";
        }
    }
}

package com.irvinsegura.supportchat.channels.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The email channel.
 *
 * @param readDelay           the pause between two reads of every mailbox
 * @param maxPerRead          the most mails taken from a mailbox in one read
 * @param tls                 false only for tests and local development, to reach a mail server without TLS
 * @param allowPrivateNetwork true only for tests and local development, to reach a mail server on the same machine
 */
@Validated
@ConfigurationProperties("supportchat.channels.email")
public record EmailProperties(@NotNull Duration readDelay, @Min(1) @Max(200) int maxPerRead,
        @NotNull Duration connectTimeout, @NotNull Duration readTimeout, boolean tls, boolean allowPrivateNetwork) {
}

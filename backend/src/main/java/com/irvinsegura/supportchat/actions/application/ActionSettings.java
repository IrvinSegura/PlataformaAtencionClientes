package com.irvinsegura.supportchat.actions.application;

import java.time.Duration;

/**
 * @param confirmationTtl how long a proposed action waits for the visitor's confirmation
 */
public record ActionSettings(Duration confirmationTtl) {
}

package com.irvinsegura.supportchat.shared.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs. Tests turn them off and call the services directly, so nothing runs at an unexpected moment.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "supportchat.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration {
}

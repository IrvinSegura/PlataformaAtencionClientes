package com.irvinsegura.supportchat.shared.infrastructure;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Module event listeners run asynchronously after the publishing transaction commits, so slow work such as
 * embedding a document never delays or rolls back the request that caused it.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {

    // Listeners keep the request's logging context (correlation id). The workspace is deliberately not propagated:
    // each listener binds it from the event it receives, so the binding is always explicit
    @Bean
    TaskDecorator loggingContextTaskDecorator() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}

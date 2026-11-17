package com.irvinsegura.supportchat.shared.web;

import com.irvinsegura.supportchat.shared.domain.StoredText;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Every text read from a JSON body (a visitor's question, a webhook from Meta, a form of the panel) loses its NUL
 * characters, which PostgreSQL would refuse: one in a question would otherwise fail the request, and one in a webhook
 * would fail every redelivery of it.
 */
@Configuration(proxyBeanMethods = false)
class StoredTextConfiguration {

    @Bean
    SimpleModule storedTextModule() {
        return new SimpleModule("stored-text").addDeserializer(String.class, new StoredTextDeserializer());
    }

    static final class StoredTextDeserializer extends StringDeserializer {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            return StoredText.of(super.deserialize(parser, context));
        }
    }
}

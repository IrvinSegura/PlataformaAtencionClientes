package com.irvinsegura.supportchat.channels.infrastructure.config;

import com.irvinsegura.supportchat.channels.application.EmailChannelService.EmailReadSettings;
import com.irvinsegura.supportchat.channels.application.OwnerAlerts.AlertSettings;
import com.irvinsegura.supportchat.channels.domain.port.EmailInbox;
import com.irvinsegura.supportchat.channels.domain.port.EmailSender;
import com.irvinsegura.supportchat.channels.domain.port.MailServerPolicy;
import com.irvinsegura.supportchat.channels.domain.port.MetaGraph;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender;
import com.irvinsegura.supportchat.channels.infrastructure.graph.GraphMetaClient;
import com.irvinsegura.supportchat.channels.infrastructure.graph.GraphWhatsAppSender;
import com.irvinsegura.supportchat.channels.infrastructure.mail.ImapEmailInbox;
import com.irvinsegura.supportchat.channels.infrastructure.mail.MailConnections;
import com.irvinsegura.supportchat.channels.infrastructure.mail.NetworkMailServerPolicy;
import com.irvinsegura.supportchat.channels.infrastructure.mail.SmtpEmailSender;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ChannelConfiguration {

    @Bean
    WhatsAppSender whatsAppSender(WhatsAppProperties properties, JsonMapper json) {
        return new GraphWhatsAppSender(http(properties.connectTimeout()), json, properties.graphBaseUrl(),
                properties.requestTimeout());
    }

    @Bean
    AlertSettings alertSettings(WhatsAppProperties properties) {
        return new AlertSettings(properties.panelUrl());
    }

    @Bean
    MetaGraph metaGraph(MetaProperties properties, JsonMapper json) {
        return new GraphMetaClient(http(properties.connectTimeout()), json, properties.graphBaseUrl(),
                properties.requestTimeout());
    }

    @Bean
    MailServerPolicy mailServerPolicy(EmailProperties properties) {
        return new NetworkMailServerPolicy(properties.allowPrivateNetwork());
    }

    @Bean
    EmailInbox emailInbox(MailServerPolicy servers, EmailProperties properties) {
        return new ImapEmailInbox(servers, connections(properties));
    }

    @Bean
    EmailSender emailSender(MailServerPolicy servers, EmailProperties properties) {
        return new SmtpEmailSender(servers, connections(properties));
    }

    @Bean
    EmailReadSettings emailReadSettings(EmailProperties properties) {
        return new EmailReadSettings(properties.maxPerRead());
    }

    private static MailConnections connections(EmailProperties properties) {
        return new MailConnections(properties.tls(), properties.connectTimeout(), properties.readTimeout());
    }

    private static HttpClient http(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}

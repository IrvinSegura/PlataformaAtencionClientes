package com.irvinsegura.supportchat.platform.infrastructure.config;

import com.irvinsegura.supportchat.platform.application.PlatformAdmins;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    // Named so @PreAuthorize can refer to it as @platformAdmins
    @Bean
    PlatformAdmins platformAdmins(PlatformProperties properties) {
        return new PlatformAdmins(properties.adminUserIds());
    }
}

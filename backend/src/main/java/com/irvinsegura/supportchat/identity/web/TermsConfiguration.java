package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.application.TermsService;
import com.irvinsegura.supportchat.shared.web.LegalProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The terms of service operators accept are the ones the public legal page shows: both read the same version.
 */
@Configuration(proxyBeanMethods = false)
class TermsConfiguration {

    @Bean
    TermsService.Settings termsSettings(LegalProperties legal) {
        return new TermsService.Settings(legal.termsVersion());
    }
}

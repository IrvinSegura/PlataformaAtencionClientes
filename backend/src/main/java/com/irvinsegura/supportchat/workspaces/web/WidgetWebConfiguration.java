package com.irvinsegura.supportchat.workspaces.web;

import com.irvinsegura.supportchat.workspaces.WidgetHeaders;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
class WidgetWebConfiguration {

    @Bean
    FilterRegistrationBean<WidgetTenantFilter> widgetTenantFilter(Workspaces workspaces,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        FilterRegistrationBean<WidgetTenantFilter> registration =
                new FilterRegistrationBean<>(new WidgetTenantFilter(workspaces, exceptionResolver));
        // After the security chain (which answers CORS preflights) and the rate limiter, so rejected requests
        // never reach the database
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);
        return registration;
    }

    /**
     * Used by Spring Security's CORS support. Widget endpoints answer only the origins their workspace allowed; the
     * key is part of the path precisely so preflight requests, which carry no custom headers, can be checked too.
     * Every other endpoint answers only the operator panel.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(Workspaces workspaces, PanelCorsProperties panel) {
        CorsConfiguration panelCors = panelConfiguration(panel.allowedOrigins());
        return request -> {
            String path = WidgetTenantFilter.path(request);
            if (!path.startsWith(WidgetTenantFilter.PREFIX)) {
                return panelCors;
            }
            // No configuration means no CORS headers, and the browser blocks the call
            return WidgetTenantFilter.widgetKey(path)
                    .flatMap(workspaces::findByWidgetKey)
                    .map(workspace -> widgetConfiguration(workspace.allowedOrigins()))
                    .orElse(null);
        };
    }

    private static CorsConfiguration panelConfiguration(List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Correlation-Id",
                "X-CSRF-Protection"));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER, "X-Correlation-Id"));
        // The refresh token travels in an HttpOnly cookie
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofMinutes(10));
        return cors;
    }

    private static CorsConfiguration widgetConfiguration(List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        // PUT: verdicts on answers and ratings, which a visitor can give again to change them
        cors.setAllowedMethods(List.of("GET", "POST", "PUT"));
        cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, WidgetHeaders.CONVERSATION_TOKEN));
        cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, "X-Correlation-Id"));
        // Visitors are anonymous: no cookies, the conversation token is sent explicitly
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofMinutes(10));
        return cors;
    }
}

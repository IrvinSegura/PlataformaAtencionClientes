package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.OperatorClaims;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API. Operators send a bearer access token; widget visitors are anonymous and are scoped by the widget
 * key and their conversation token instead. Everything not listed here requires an operator.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint entryPoint = problemDetailsEntryPoint(exceptionResolver);
        AccessDeniedHandler accessDeniedHandler = problemDetailsAccessDeniedHandler(exceptionResolver);
        http
                // No server sessions: access tokens travel in the Authorization header, and the refresh cookie is
                // only accepted with a custom header that a cross-site request cannot add (see AuthController)
                .csrf(AbstractHttpConfigurer::disable)
                // Rules come from the corsConfigurationSource bean: the panel's origins, or each workspace's own
                // origins for widget endpoints. Preflights are answered before authentication
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // The API serves JSON and event streams only; nothing should be framed or run scripts
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        // An event stream ends with an async dispatch of a request already authorized when it
                        // started; the bearer token is not read again there, so it would look anonymous
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/v1/widget/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // Whoever holds an invitation link; the token in the body is the credential
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/preview",
                                "/api/v1/invitations/acceptance").permitAll()
                        // Visitors: the widget key scopes them to a workspace, the conversation token to a chat
                        .requestMatchers("/api/v1/widget/**").permitAll()
                        // Meta: the channel key selects the workspace, the signature over the body proves the sender
                        .requestMatchers("/api/v1/channels/whatsapp/*/webhook", "/api/v1/channels/messenger/*/webhook",
                                "/api/v1/channels/instagram/*/webhook").permitAll()
                        .requestMatchers("/api/v1/billing/stripe/webhook").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Only served in the dev profile, see springdoc settings
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Public legal pages, such as the privacy policy Meta links to
                        .requestMatchers(HttpMethod.GET, "/legal/**").permitAll()
                        // Link checkers often ask with HEAD first
                        .requestMatchers(HttpMethod.HEAD, "/legal/**").permitAll()
                        // The metrics collector scrapes them on the management port, which this chain also guards
                        // and which never leaves the private network; the public port refuses them
                        .requestMatchers(onManagementPort(environment, "/actuator/prometheus")).permitAll()
                        // Platform metrics are for whoever runs it, never for workspace operators
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    FilterRegistrationBean<OperatorTenantFilter> operatorTenantFilter() {
        FilterRegistrationBean<OperatorTenantFilter> registration =
                new FilterRegistrationBean<>(new OperatorTenantFilter());
        // After the security chain has authenticated the token
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 3);
        return registration;
    }

    // Only a separate management server (prod) counts. Without one (dev, tests, a deploy that leaves MANAGEMENT_PORT
    // out) Spring Boot still publishes local.management.port as an alias of the public port, so it is compared too
    static RequestMatcher onManagementPort(Environment environment, String path) {
        return request -> {
            Integer port = environment.getProperty("local.management.port", Integer.class);
            Integer publicPort = environment.getProperty("local.server.port", Integer.class);
            return port != null && !port.equals(publicPort) && port == request.getLocalPort()
                    && path.equals(request.getRequestURI());
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(OperatorClaims.ROLES);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // The bearer handlers set the status and WWW-Authenticate header (RFC 6750); the exception resolver then writes
    // the same Problem Details body as any other error
    private static AuthenticationEntryPoint problemDetailsEntryPoint(HandlerExceptionResolver exceptionResolver) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, ex) -> {
            bearer.commence(request, response, ex);
            exceptionResolver.resolveException(request, response, null, ex);
        };
    }

    private static AccessDeniedHandler problemDetailsAccessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, ex) -> {
            bearer.handle(request, response, ex);
            exceptionResolver.resolveException(request, response, null, ex);
        };
    }
}

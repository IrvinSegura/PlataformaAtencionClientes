package com.irvinsegura.supportchat.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Metrics open only on a management server of their own. ActuatorAccessIntegrationTest covers that case end to end;
 * these cover the others without starting one.
 */
class ManagementPortMatcherTest {

    private static final String PATH = "/actuator/prometheus";

    @Test
    void opensOnASeparateManagementPort() {
        RequestMatcher matcher = matcher(8080, 8081);

        assertThat(matcher.matches(request(8081, PATH))).isTrue();
        assertThat(matcher.matches(request(8080, PATH))).isFalse();
        assertThat(matcher.matches(request(8081, "/actuator/env"))).isFalse();
    }

    @Test
    void staysClosedWhenManagementSharesThePublicPort() {
        // Spring Boot then publishes local.management.port as an alias of local.server.port
        assertThat(matcher(8080, 8080).matches(request(8080, PATH))).isFalse();
    }

    @Test
    void staysClosedWithoutAWebServer() {
        assertThat(SecurityConfiguration.onManagementPort(new MockEnvironment(), PATH).matches(request(8080, PATH)))
                .isFalse();
    }

    private static RequestMatcher matcher(int serverPort, int managementPort) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("local.server.port", String.valueOf(serverPort))
                .withProperty("local.management.port", String.valueOf(managementPort));
        return SecurityConfiguration.onManagementPort(environment, PATH);
    }

    private static MockHttpServletRequest request(int port, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setLocalPort(port);
        return request;
    }
}

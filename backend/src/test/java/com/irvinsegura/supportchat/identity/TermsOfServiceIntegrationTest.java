package com.irvinsegura.supportchat.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.WithExternalServices;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * No account exists without the terms of service accepted, each acceptance is kept with its version, and an operator
 * who has not accepted the version in force is told so until they do.
 */
class TermsOfServiceIntegrationTest extends WithExternalServices {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String VERSION = "2026-11-11";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc);
    }

    @Test
    void anAccountIsNotCreatedWithoutAcceptingTheTerms() throws Exception {
        String email = "terms-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(ApiClient.json("{'email':'%s','password':'%s','displayName':'Ana',"
                                + "'workspaceName':'Acme','acceptTerms':false}", email, PASSWORD)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(ApiClient.json("{'email':'%s','password':'%s','displayName':'Ana',"
                                + "'workspaceName':'Acme'}", email, PASSWORD)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));

        // Nothing was saved: the same email registers once the terms are accepted
        api.register(email, PASSWORD, "Acme").andExpect(status().isCreated());
    }

    @Test
    void signingUpKeepsTheAcceptanceOfTheVersionInForce() throws Exception {
        Operator owner = api.newOperator("Acme");

        api.getAs(owner, "/api/v1/me")
                .andExpect(jsonPath("$.termsVersion").value(VERSION))
                .andExpect(jsonPath("$.termsAccepted").value(true));
        assertThat(jdbc.sql(
                        "SELECT count(*) FROM terms_acceptances WHERE user_id = :userId AND version = :version")
                .param("userId", userId(owner))
                .param("version", VERSION)
                .query(Long.class)
                .single()).isEqualTo(1);
    }

    @Test
    void aNewAccountJoiningByInvitationAcceptsTheTermsToo() throws Exception {
        Operator owner = api.newOperator("Acme");
        String token = api.invite(owner, "laura-" + UUID.randomUUID() + "@example.com", "AGENT");

        mockMvc.perform(post("/api/v1/invitations/acceptance").contentType(MediaType.APPLICATION_JSON)
                        .content(ApiClient.json("{'token':'%s','displayName':'Laura','password':'%s'}", token,
                                PASSWORD)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));

        api.acceptInvitation(token, "Laura", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anOperatorWhoHasNotAcceptedTheVersionInForceAcceptsItInThePanel() throws Exception {
        Operator owner = api.newOperator("Acme");
        // As if the terms had changed since they signed up
        jdbc.sql("DELETE FROM terms_acceptances WHERE user_id = :userId").param("userId", userId(owner)).update();
        api.getAs(owner, "/api/v1/me").andExpect(jsonPath("$.termsAccepted").value(false));

        // The panel showed an older version: it must show the one in force first
        api.postJsonAs(owner, "/api/v1/me/terms-acceptance", ApiClient.json("{'version':'2020-01-01'}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));

        api.postJsonAs(owner, "/api/v1/me/terms-acceptance", ApiClient.json("{'version':'%s'}", VERSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAccepted").value(true));
        // Accepting again changes nothing
        api.postJsonAs(owner, "/api/v1/me/terms-acceptance", ApiClient.json("{'version':'%s'}", VERSION))
                .andExpect(status().isOk());
        api.getAs(owner, "/api/v1/me").andExpect(jsonPath("$.termsAccepted").value(true));
    }

    @Test
    void acceptingNeedsASignedInOperator() throws Exception {
        mockMvc.perform(post("/api/v1/me/terms-acceptance").contentType(MediaType.APPLICATION_JSON)
                        .content(ApiClient.json("{'version':'%s'}", VERSION)))
                .andExpect(status().isUnauthorized());
    }

    private UUID userId(Operator operator) throws Exception {
        String body = api.getAs(operator, "/api/v1/me").andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}

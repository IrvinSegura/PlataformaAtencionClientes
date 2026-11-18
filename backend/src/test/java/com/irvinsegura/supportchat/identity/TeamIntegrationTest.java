package com.irvinsegura.supportchat.identity;

import static com.irvinsegura.supportchat.support.ApiClient.CSRF_HEADER;
import static com.irvinsegura.supportchat.support.ApiClient.PASSWORD;
import static com.irvinsegura.supportchat.support.ApiClient.json;
import static com.irvinsegura.supportchat.support.ApiClient.uniqueEmail;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Bringing people into a workspace through invitation links, and what each role may do to the team.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TeamIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;
    private Operator owner;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
    }

    @Test
    void someoneWithoutAnAccountJoinsThroughTheLinkOnce() throws Exception {
        String email = uniqueEmail();
        String token = api.invite(owner, email, "AGENT");

        preview(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Acme"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.accountExists").value(false));

        api.acceptInvitation(token, "Laura", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(owner.workspaceId().toString()))
                .andExpect(jsonPath("$.role").value("AGENT"));

        api.getAs(owner, "/api/v1/workspace/members").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].role", containsInAnyOrder("OWNER", "AGENT")));
        api.acceptInvitation(token, "Laura", PASSWORD).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_INVITATION"));
        api.getAs(owner, "/api/v1/workspace/invitations").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anExistingAccountProvesItsPasswordAndCanSwitchBetweenWorkspaces() throws Exception {
        Operator other = api.newOperator("Other shop");
        String token = api.invite(owner, other.email(), "ADMIN");
        preview(token).andExpect(jsonPath("$.accountExists").value(true));

        api.acceptInvitation(token, "", "not the password at all").andExpect(status().isUnauthorized());
        String body = api.acceptInvitation(token, "", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(owner.workspaceId().toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        Operator asAdmin = new Operator(other.email(), accessToken, owner.workspaceId(), other.refreshCookie());

        api.getAs(asAdmin, "/api/v1/me/workspaces").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].workspaceName", containsInAnyOrder("Acme", "Other shop")));
        switchTo(asAdmin, other.workspaceId().toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(other.workspaceId().toString()))
                .andExpect(jsonPath("$.role").value("OWNER"));
        switchTo(asAdmin, api.newOperator("Stranger").workspaceId().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void eachRoleOnlyManagesTheMembersBelowIt() throws Exception {
        Operator admin = api.newMember(owner, "ADMIN");
        Operator agent = api.newMember(owner, "AGENT");

        api.postJsonAs(agent, "/api/v1/workspace/invitations", json("{'email':'%s','role':'AGENT'}", uniqueEmail()))
                .andExpect(status().isForbidden());
        api.postJsonAs(admin, "/api/v1/workspace/invitations", json("{'email':'%s','role':'ADMIN'}", uniqueEmail()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_ACTION_NOT_ALLOWED"));
        api.postJsonAs(owner, "/api/v1/workspace/invitations", json("{'email':'%s','role':'OWNER'}", uniqueEmail()))
                .andExpect(status().isForbidden());
        api.putJsonAs(admin, "/api/v1/workspace/members/" + memberId(agent) + "/role", "{\"role\":\"ADMIN\"}")
                .andExpect(status().isForbidden());
        api.deleteAs(admin, "/api/v1/workspace/members/" + memberId(owner))
                .andExpect(status().isForbidden());

        api.putJsonAs(owner, "/api/v1/workspace/members/" + memberId(agent) + "/role", "{\"role\":\"ADMIN\"}")
                .andExpect(status().isNoContent());
        api.deleteAs(owner, "/api/v1/workspace/members/" + memberId(agent)).andExpect(status().isNoContent());
        // The removed member's login ends at the next refresh
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(agent.refreshCookie()).header(CSRF_HEADER, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRevokedOrReplacedLinkStopsWorking() throws Exception {
        String email = uniqueEmail();
        String first = api.invite(owner, email, "AGENT");
        String second = api.invite(owner, email, "AGENT");
        preview(first).andExpect(status().isNotFound());

        String invitationId = JsonPath.read(api.getAs(owner, "/api/v1/workspace/invitations")
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        api.deleteAs(owner, "/api/v1/workspace/invitations/" + invitationId).andExpect(status().isNoContent());
        preview(second).andExpect(status().isNotFound());
        preview("made-up-token").andExpect(status().isNotFound());
    }

    private ResultActions preview(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'token':'%s'}", token)));
    }

    private ResultActions switchTo(Operator operator, String workspaceId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/workspace-switch")
                .header(HttpHeaders.AUTHORIZATION, operator.bearer())
                .header(CSRF_HEADER, "1")
                .cookie(operator.refreshCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'workspaceId':'%s'}", workspaceId)));
    }

    private String memberId(Operator operator) throws Exception {
        return JsonPath.read(api.getAs(operator, "/api/v1/me").andReturn().getResponse().getContentAsString(), "$.id");
    }
}

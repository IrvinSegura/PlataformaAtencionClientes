package com.irvinsegura.supportchat.platform;

import static com.irvinsegura.supportchat.support.ApiClient.json;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.platform.application.PlatformAdmins;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Whoever runs the platform sees every workspace with its owner, plan and activity, and suspends one: its assistant
 * stops answering and hands the conversations to the team until it is reinstated. Nobody else reaches any of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlatformAdministrationIntegrationTest {

    private static final String SITE = "https://shop.example.com";

    @Autowired
    private MockMvc mockMvc;

    // Platform administrators are configured by user id, which is only known once the test signs one up
    @MockitoSpyBean
    private PlatformAdmins admins;

    private ApiClient api;
    private Operator admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        admin = api.newOperator("Platform HQ");
        String me = api.getAs(admin, "/api/v1/me").andReturn().getResponse().getContentAsString();
        doReturn(true).when(admins).isAdmin(UUID.fromString(JsonPath.read(me, "$.id")));
    }

    @Test
    void anAdministratorSeesEveryWorkspaceAndSuspendsOneUntilItIsReinstated() throws Exception {
        String name = "Tienda " + UUID.randomUUID();
        Operator owner = api.newOperator(name);
        String widgetKey = api.enableWidget(owner, SITE);
        Visitor visitor = api.startConversation(widgetKey, SITE);
        api.ask(visitor, "¿Hacen envíos?").andExpect(status().isOk());

        api.getAs(admin, "/api/v1/platform/me").andExpect(jsonPath("$.platformAdmin").value(true));
        api.getAs(admin, "/api/v1/platform/workspaces?query=" + name)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(owner.workspaceId().toString()))
                .andExpect(jsonPath("$.items[0].name").value(name))
                .andExpect(jsonPath("$.items[0].members").value(1))
                .andExpect(jsonPath("$.items[0].ownerEmail").value(owner.email()))
                .andExpect(jsonPath("$.items[0].plan.planCode").isNotEmpty())
                .andExpect(jsonPath("$.items[0].conversationsLastMonth").value(1))
                .andExpect(jsonPath("$.items[0].lastActivityAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].suspension").value(nullValue()));

        String suspension = "/api/v1/platform/workspaces/" + owner.workspaceId() + "/suspension";
        api.postJsonAs(admin, suspension, json("{'reason':'   '}")).andExpect(status().isBadRequest());
        api.postJsonAs(admin, suspension, json("{'reason':'Envío de spam'}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspension.reason").value("Envío de spam"));
        api.getAs(owner, "/api/v1/workspace").andExpect(jsonPath("$.suspension.reason").value("Envío de spam"));

        // Suspended, the assistant answers nothing: the conversation waits for the team
        Visitor next = api.startConversation(widgetKey, SITE);
        api.ask(next, "¿Hacen envíos?").andExpect(status().isOk());
        api.getAs(owner, "/api/v1/conversations?handling=WAITING")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(next.conversationId().toString()));

        api.deleteAs(admin, suspension)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspension").value(nullValue()));
        api.getAs(owner, "/api/v1/workspace").andExpect(jsonPath("$.suspension").value(nullValue()));
        Visitor after = api.startConversation(widgetKey, SITE);
        api.ask(after, "¿Hacen envíos?").andExpect(status().isOk());
        api.getAs(owner, "/api/v1/conversations?handling=WAITING").andExpect(jsonPath("$.total").value(1));

        api.postJsonAs(admin, "/api/v1/platform/workspaces/" + UUID.randomUUID() + "/suspension",
                json("{'reason':'Spam'}")).andExpect(status().isNotFound());
    }

    @Test
    void nobodyElseReachesThePlatformAdministration() throws Exception {
        Operator owner = api.newOperator("Acme");

        api.getAs(owner, "/api/v1/platform/me").andExpect(jsonPath("$.platformAdmin").value(false));
        api.getAs(owner, "/api/v1/platform/workspaces").andExpect(status().isForbidden());
        api.getAs(owner, "/api/v1/platform/workspaces/" + owner.workspaceId()).andExpect(status().isForbidden());
        api.postJsonAs(owner, "/api/v1/platform/workspaces/" + owner.workspaceId() + "/suspension",
                json("{'reason':'Spam'}")).andExpect(status().isForbidden());
        api.deleteAs(owner, "/api/v1/platform/workspaces/" + owner.workspaceId() + "/suspension")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/workspaces")).andExpect(status().isUnauthorized());
    }
}

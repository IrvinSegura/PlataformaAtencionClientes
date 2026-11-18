package com.irvinsegura.supportchat.identity;

import static com.irvinsegura.supportchat.support.ApiClient.CSRF_HEADER;
import static com.irvinsegura.supportchat.support.ApiClient.PASSWORD;
import static com.irvinsegura.supportchat.support.ApiClient.REFRESH_COOKIE;
import static com.irvinsegura.supportchat.support.ApiClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc);
    }

    @Test
    void registeringCreatesTheOwnerOfANewWorkspaceAndSignsIn() throws Exception {
        String email = uniqueEmail();

        MvcResult result = api.register(email, PASSWORD, "Acme")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.workspaceId").isNotEmpty())
                // The refresh token travels only in the HttpOnly cookie, never where scripts can read it
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");

        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnce() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD, "First").andExpect(status().isCreated());

        api.register(email.toUpperCase(), PASSWORD, "Second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        api.register(uniqueEmail(), "aaaaaaaaaaaaaaaa", "Acme")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        Operator operator = api.newOperator("Acme");

        api.login(operator.email(), "wrong password here")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        api.login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        Operator operator = api.newOperator("Acme");
        for (int attempt = 0; attempt < 5; attempt++) {
            api.login(operator.email(), "wrong password here").andExpect(status().isUnauthorized());
        }

        api.login(operator.email(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesTheTokenAndReusingAnOldOneEndsTheWholeLogin() throws Exception {
        Operator operator = api.newOperator("Acme");
        Cookie first = operator.refreshCookie();

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Someone replays the first token (e.g. stolen): rejected, and the legitimate one dies with it
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshNeedsTheCsrfHeaderSoOtherSitesCannotTriggerIt() throws Exception {
        Operator operator = api.newOperator("Acme");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(operator.refreshCookie()))
                .andExpect(status().isBadRequest());
        // The cookie was not used up by the rejected request
        refresh(operator.refreshCookie()).andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheLogin() throws Exception {
        Operator operator = api.newOperator("Acme");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(operator.refreshCookie()).header(CSRF_HEADER, "1"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        refresh(operator.refreshCookie()).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header(CSRF_HEADER, "1"));
    }
}

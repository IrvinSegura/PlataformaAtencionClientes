package com.irvinsegura.supportchat.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sign-up says whether an email already has an account, so one address gets few attempts: too few to list the
 * accounts with it. The other tests run without rate limits; this one has them, as in production.
 */
@SpringBootTest(properties = "supportchat.rate-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SignUpRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oneAddressCannotTrySignUpOverAndOver() throws Exception {
        // Invalid bodies: the limit counts every attempt, before the request is read
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}

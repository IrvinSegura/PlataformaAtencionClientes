package com.irvinsegura.supportchat.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.support.WithExternalServices;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What every response carries whatever handles it. Errors the servlet container hands to the error page, such as a
 * request the security firewall rejects, read like every other error; MockMvc does not forward to the error page, so
 * the test calls it as the container would.
 */
class ResponseBasicsIntegrationTest extends WithExternalServices {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aRejectedRequestGetsAProblemDetailsBody() throws Exception {
        mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void aRequestThatCameOverHttpsIsToldToKeepUsingIt() throws Exception {
        mockMvc.perform(get("/api/v1/me").secure(true))
                .andExpect(header().string("Strict-Transport-Security",
                        org.hamcrest.Matchers.startsWith("max-age=31536000")));
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void aServerErrorSaysNothingAboutItsCause() throws Exception {
        mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}

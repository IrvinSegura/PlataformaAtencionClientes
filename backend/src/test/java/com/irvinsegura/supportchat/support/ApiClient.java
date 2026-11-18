package com.irvinsegura.supportchat.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Drives the API the way the panel and the widget do, so tests read as user journeys.
 */
public final class ApiClient {

    public static final String PASSWORD = "correct horse battery staple";
    public static final String CSRF_HEADER = "X-CSRF-Protection";
    public static final String REFRESH_COOKIE = "supportchat_refresh";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final MockMvc mockMvc;

    public ApiClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public static String uniqueEmail() {
        return "operator-" + UUID.randomUUID().toString().substring(0, 8) + "-" + SEQUENCE.incrementAndGet()
                + "@example.com";
    }

    public static String json(String template, Object... args) {
        return template.formatted(args).replace('\'', '"');
    }

    public ResultActions register(String email, String password, String workspaceName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s','displayName':'Operator','workspaceName':'%s',"
                        + "'acceptTerms':true}", email, password, workspaceName)));
    }

    public ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s'}", email, password)));
    }

    /**
     * Registers an operator with a new workspace.
     */
    public Operator newOperator(String workspaceName) throws Exception {
        String email = uniqueEmail();
        MvcResult result = register(email, PASSWORD, workspaceName).andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new Operator(email, JsonPath.read(body, "$.accessToken"),
                UUID.fromString(JsonPath.read(body, "$.workspaceId")),
                result.getResponse().getCookie(REFRESH_COOKIE));
    }

    public ResultActions getAs(Operator operator, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, operator.bearer()));
    }

    public ResultActions postJsonAs(Operator operator, String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, operator.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    public ResultActions postAs(Operator operator, String path) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, operator.bearer()));
    }

    public ResultActions putJsonAs(Operator operator, String path, String body) throws Exception {
        return mockMvc.perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, operator.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    public ResultActions deleteAs(Operator operator, String path) throws Exception {
        return mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, operator.bearer()));
    }

    /**
     * Invites someone to the operator's workspace and returns the token of the link.
     */
    public String invite(Operator operator, String email, String role) throws Exception {
        String body = postJsonAs(operator, "/api/v1/workspace/invitations",
                        json("{'email':'%s','role':'%s'}", email, role))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    public ResultActions acceptInvitation(String token, String displayName, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/acceptance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'token':'%s','displayName':'%s','password':'%s','acceptTerms':true}", token, displayName,
                        password)));
    }

    /**
     * Invites a new person and signs them in through the link.
     */
    public Operator newMember(Operator inviter, String role) throws Exception {
        String email = uniqueEmail();
        MvcResult result = acceptInvitation(invite(inviter, email, role), "Laura", PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new Operator(email, JsonPath.read(body, "$.accessToken"),
                UUID.fromString(JsonPath.read(body, "$.workspaceId")),
                result.getResponse().getCookie(REFRESH_COOKIE));
    }

    /**
     * A widget call about the visitor's conversation, such as {@code /handoff}.
     */
    public ResultActions visitorPost(Visitor visitor, String subPath) throws Exception {
        return mockMvc.perform(post("/api/v1/widget/{key}/conversations/{id}" + subPath, visitor.widgetKey(),
                visitor.conversationId())
                .header(HttpHeaders.ORIGIN, visitor.origin())
                .header("X-Conversation-Token", visitor.token()));
    }

    /**
     * A widget call with a JSON body about the visitor's conversation, such as {@code /rating}.
     */
    public ResultActions visitorPutJson(Visitor visitor, String subPath, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/widget/{key}/conversations/{id}" + subPath, visitor.widgetKey(),
                visitor.conversationId())
                .header(HttpHeaders.ORIGIN, visitor.origin())
                .header("X-Conversation-Token", visitor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    public ResultActions visitorHistory(Visitor visitor) throws Exception {
        return mockMvc.perform(get("/api/v1/widget/{key}/conversations/{id}/messages", visitor.widgetKey(),
                visitor.conversationId())
                .header(HttpHeaders.ORIGIN, visitor.origin())
                .header("X-Conversation-Token", visitor.token()));
    }

    public ResultActions upload(Operator operator, String fileName, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/knowledge/documents")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, operator.bearer()));
    }

    /**
     * Uploads a document and waits until the background ingestion has made it searchable.
     */
    public UUID uploadAndWaitUntilReady(Operator operator, String fileName, String content) throws Exception {
        String body = upload(operator, fileName, content).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(body, "$.id"));
        Eventually.assertEventually(Duration.ofSeconds(20), () -> {
            String document = getAs(operator, "/api/v1/knowledge/documents/" + documentId)
                    .andReturn().getResponse().getContentAsString();
            assertThat((String) JsonPath.read(document, "$.status")).isEqualTo("READY");
        });
        return documentId;
    }

    /**
     * Lets the widget run on {@code origin} and returns the workspace's widget key.
     */
    public String enableWidget(Operator operator, String origin) throws Exception {
        String body = mockMvc.perform(put("/api/v1/workspace/widget-settings")
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'botName':'Ana','welcomeMessage':'Hola','allowedOrigins':['%s']}", origin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.widgetKey");
    }

    public Visitor startConversation(String widgetKey, String origin) throws Exception {
        return startConversation(widgetKey, origin, null);
    }

    /**
     * @param acceptLanguage the visitor's browser languages, e.g. "en-US,en;q=0.9"; null sends none
     */
    public Visitor startConversation(String widgetKey, String origin, String acceptLanguage) throws Exception {
        var request = post("/api/v1/widget/{key}/conversations", widgetKey).header(HttpHeaders.ORIGIN, origin);
        if (acceptLanguage != null) {
            request = request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Visitor(widgetKey, origin, UUID.fromString(JsonPath.read(body, "$.conversationId")),
                JsonPath.read(body, "$.visitorToken"));
    }

    public ResultActions ask(Visitor visitor, String question) throws Exception {
        return mockMvc.perform(post("/api/v1/widget/{key}/conversations/{id}/messages", visitor.widgetKey(),
                visitor.conversationId())
                .header(HttpHeaders.ORIGIN, visitor.origin())
                .header("X-Conversation-Token", visitor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(json("{'question':'%s'}", question)));
    }

    public record Operator(String email, String accessToken, UUID workspaceId, Cookie refreshCookie) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public record Visitor(String widgetKey, String origin, UUID conversationId, String token) {
    }

    /**
     * The events of a server-sent event stream, in order.
     */
    public static List<SseEvent> events(String stream) {
        return java.util.Arrays.stream(stream.split("\n\n"))
                .filter(frame -> !frame.isBlank())
                .map(frame -> {
                    String[] lines = frame.split("\n");
                    return new SseEvent(lines[0].substring("event: ".length()), lines[1].substring("data: ".length()));
                })
                .toList();
    }

    public record SseEvent(String name, String data) {
    }
}

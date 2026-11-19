package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.irvinsegura.supportchat.support.Eventually;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Live updates travel through PostgreSQL, so a change made by any instance reaches streams opened on any other.
 * Another instance is simulated by notifying the channel from a separate database connection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LiveUpdatesAcrossInstancesIntegrationTest {

    private static final String SITE = "https://shop.example.com";
    private static final String CHANNEL = "conversation_events";
    private static final Pattern STATE_EVENT = Pattern.compile("event:state");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostgreSQLContainer postgres;

    private ApiClient api;
    private Operator owner;
    private Visitor visitor;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        owner = api.newOperator("Acme");
        String widgetKey = api.enableWidget(owner, SITE);
        visitor = api.startConversation(widgetKey, SITE);
    }

    @Test
    void aChangeAnnouncedByAnotherInstanceReachesTheStreamsOpenedHere() throws Exception {
        MvcResult stream = openVisitorStream();
        assertThat(count(stream)).isEqualTo(1);

        notifyAsAnotherInstance("CONVERSATION_CHANGED:" + owner.workspaceId() + ":" + visitor.conversationId());

        Eventually.assertEventually(Duration.ofSeconds(5), () -> assertThat(count(stream)).isEqualTo(2));
    }

    @Test
    void afterLosingTheDatabaseConnectionStreamsAreEndedAndLiveUpdatesResume() throws Exception {
        MvcResult before = openVisitorStream();

        int lostConnection = terminateListenerConnection();
        Eventually.assertEventually(Duration.ofSeconds(10),
                () -> assertThat(listenersOtherThan(lostConnection)).isEqualTo(1));

        MvcResult after = openVisitorStream();
        notifyAsAnotherInstance("CONVERSATION_CHANGED:" + owner.workspaceId() + ":" + visitor.conversationId());
        Eventually.assertEventually(Duration.ofSeconds(5), () -> assertThat(count(after)).isEqualTo(2));
        // The stream open while events were lost was ended, so its client reconnects and reloads what it missed
        assertThat(count(before)).isEqualTo(1);
    }

    @Test
    void aMalformedNotificationIsIgnored() throws Exception {
        MvcResult stream = openVisitorStream();

        notifyAsAnotherInstance("not an event");
        api.postAs(owner, "/api/v1/conversations/" + visitor.conversationId() + "/takeover")
                .andExpect(status().isOk());

        Eventually.assertEventually(Duration.ofSeconds(5),
                () -> assertThat(stream.getResponse().getContentAsString()).contains("AGENT_JOINED"));
    }

    private MvcResult openVisitorStream() throws Exception {
        return mockMvc.perform(get("/api/v1/widget/{key}/conversations/{id}/events", visitor.widgetKey(),
                        visitor.conversationId())
                        .header(HttpHeaders.ORIGIN, SITE)
                        .header("X-Conversation-Token", visitor.token()))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static int count(MvcResult stream) throws Exception {
        Matcher matcher = STATE_EVENT.matcher(stream.getResponse().getContentAsString());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private void notifyAsAnotherInstance(String payload) throws Exception {
        try (Connection connection = superuser();
                PreparedStatement statement = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            statement.setString(1, CHANNEL);
            statement.setString(2, payload);
            statement.execute();
        }
    }

    private int terminateListenerConnection() throws Exception {
        try (Connection connection = superuser();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT pid, pg_terminate_backend(pid) FROM pg_stat_activity
                        WHERE application_name = 'supportchat-live-updates'
                        """);
                ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            int pid = result.getInt(1);
            assertThat(result.next()).as("a single listening connection").isFalse();
            return pid;
        }
    }

    private int listenersOtherThan(int pid) throws Exception {
        try (Connection connection = superuser();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE application_name = 'supportchat-live-updates' AND pid <> ? AND query LIKE 'LISTEN%'
                        """)) {
            statement.setInt(1, pid);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private Connection superuser() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}

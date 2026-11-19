package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The business meters move with what customers do, and their tags never name a workspace, so dashboards and alerts
 * work for the whole platform. Other tests share the registry, hence the before and after readings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BusinessMetricsIntegrationTest {

    private static final String SITE = "https://shop.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private PrometheusMeterRegistry prometheus;

    private ApiClient api;
    private String widgetKey;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        Operator owner = api.newOperator("Acme");
        widgetKey = api.enableWidget(owner, SITE);
    }

    @Test
    void conversationsAnswersFeedbackAndHandoffsAreCounted() throws Exception {
        double started = count("supportchat.conversations.started", "channel", "WEB");
        long answers = timed("supportchat.answer.duration", "channel", "WEB");
        double helpful = count("supportchat.feedback.answers", "helpful", "true");
        long ratings = rated();
        double handoffs = count("supportchat.conversations.handoffs", "reason", "visitor");

        Visitor visitor = api.startConversation(widgetKey, SITE);
        String answerId = answer(visitor, "Hola");
        api.visitorPutJson(visitor, "/messages/" + answerId + "/feedback", "{\"helpful\":true}")
                .andExpect(status().isNoContent());
        api.visitorPutJson(visitor, "/rating", "{\"score\":4}").andExpect(status().isNoContent());
        api.visitorPost(visitor, "/handoff").andExpect(status().isOk());
        // Asking twice changes nothing
        api.visitorPost(visitor, "/handoff").andExpect(status().isOk());

        assertThat(count("supportchat.conversations.started", "channel", "WEB")).isEqualTo(started + 1);
        assertThat(timed("supportchat.answer.duration", "channel", "WEB")).isEqualTo(answers + 1);
        assertThat(count("supportchat.feedback.answers", "helpful", "true")).isEqualTo(helpful + 1);
        assertThat(rated()).isEqualTo(ratings + 1);
        assertThat(count("supportchat.conversations.handoffs", "reason", "visitor")).isEqualTo(handoffs + 1);
        assertThat(registry.find("supportchat.conversations.started").meters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .allSatisfy(tag -> assertThat(tag.getKey()).isIn("channel", "application")));
        // The names the alerts and the Grafana dashboard in deploy/monitoring query
        assertThat(prometheus.scrape())
                .contains("supportchat_conversations_started_total{")
                .contains("supportchat_conversations_handoffs_total{")
                .contains("supportchat_answer_duration_seconds_bucket{")
                .contains("supportchat_feedback_answers_total{")
                .contains("supportchat_feedback_ratings_count{");
    }

    private double count(String name, String tag, String value) {
        Counter counter = registry.find(name).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }

    private long timed(String name, String tag, String value) {
        return registry.find(name).tag(tag, value).timers().stream().mapToLong(Timer::count).sum();
    }

    private long rated() {
        DistributionSummary summary = registry.find("supportchat.feedback.ratings").summary();
        return summary == null ? 0 : summary.count();
    }

    private String answer(Visitor visitor, String question) throws Exception {
        List<SseEvent> events = ApiClient.events(api.ask(visitor, question).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(events.getLast().name()).isEqualTo("done");
        return JsonPath.read(events.getLast().data(), "$.messageId");
    }
}

package com.irvinsegura.supportchat.conversations;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.TestcontainersConfiguration;
import com.irvinsegura.supportchat.support.ApiClient;
import com.irvinsegura.supportchat.support.ApiClient.Operator;
import com.irvinsegura.supportchat.support.ApiClient.SseEvent;
import com.irvinsegura.supportchat.support.ApiClient.Visitor;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Measures answer quality on a fixed set of questions about a small store's knowledge base: does the assistant
 * answer what the documents cover, with the right facts and citing the right document, and decline what they don't?
 * <p>
 * Runs in every build with the offline model, which guards retrieval and source selection against regressions. With
 * {@code CHAT_MODEL_PROVIDER=anthropic} (and optionally {@code EMBEDDING_PROVIDER=voyage}) the same cases measure the
 * real model; CI does that in a separate job that has the API keys. A report is written to
 * {@code target/eval/answer-quality.md} either way.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AnswerQualityEvaluationTest {

    private static final String SITE = "https://shop.example.com";
    private static final Path REPORT = Path.of("target", "eval", "answer-quality.md");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Value("${supportchat.conversations.chat-model.provider}")
    private String chatModel;

    @Value("${supportchat.knowledge.embedding.provider}")
    private String embeddingModel;

    // Minimums the build enforces; a regression below them fails it. The offline model gets every case right, and
    // the live job uses the same 0.9 so a real model may miss one case without failing the build
    @Value("${eval.min-outcome-accuracy:0.9}")
    private double minOutcomeAccuracy;

    @Value("${eval.min-fact-recall:0.9}")
    private double minFactRecall;

    @Value("${eval.min-citation-accuracy:0.9}")
    private double minCitationAccuracy;

    @Test
    void answersStayGroundedAndDeclineWhatTheKnowledgeBaseDoesNotCover() throws Exception {
        ApiClient api = new ApiClient(mockMvc);
        Operator operator = api.newOperator("Tienda de prueba");
        String widgetKey = api.enableWidget(operator, SITE);
        Map<String, UUID> documents = new HashMap<>();
        for (Resource document : new PathMatchingResourcePatternResolver()
                .getResources("classpath:eval/knowledge/*.md")) {
            documents.put(document.getFilename(), api.uploadAndWaitUntilReady(operator, document.getFilename(),
                    document.getContentAsString(StandardCharsets.UTF_8)));
        }
        List<EvalCase> cases = json.readValue(new ClassPathResource("eval/cases.json").getInputStream(),
                new TypeReference<>() {
                });

        List<CaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            // A new conversation per question, so earlier answers cannot help or hurt
            Visitor visitor = api.startConversation(widgetKey, SITE);
            String stream = api.ask(visitor, evalCase.question()).andReturn().getResponse().getContentAsString();
            results.add(evaluate(evalCase, ApiClient.events(stream), documents));
        }

        Metrics metrics = Metrics.of(results);
        writeReport(results, metrics);
        assertThat(metrics.outcomeAccuracy()).as("share of questions answered or declined as expected")
                .isGreaterThanOrEqualTo(minOutcomeAccuracy);
        assertThat(metrics.factRecall()).as("share of expected facts present in answers")
                .isGreaterThanOrEqualTo(minFactRecall);
        assertThat(metrics.citationAccuracy()).as("share of answers citing the expected document")
                .isGreaterThanOrEqualTo(minCitationAccuracy);
    }

    private static CaseResult evaluate(EvalCase evalCase, List<SseEvent> events, Map<String, UUID> documents) {
        String answer = events.stream().filter(event -> event.name().equals("delta"))
                .map(event -> (String) JsonPath.read(event.data(), "$.text"))
                .reduce("", String::concat);
        SseEvent done = events.stream().filter(event -> event.name().equals("done")).findFirst().orElse(null);
        String outcome = done == null ? "MISSING" : JsonPath.read(done.data(), "$.outcome");
        List<String> cited = done == null ? List.of() : JsonPath.read(done.data(), "$.citations[*].documentId");

        boolean expectsAnswer = evalCase.expect().equals("ANSWERED");
        int factsFound = 0;
        if (expectsAnswer) {
            String normalizedAnswer = normalize(answer);
            factsFound = (int) evalCase.facts().stream().filter(fact -> normalizedAnswer.contains(normalize(fact)))
                    .count();
        }
        boolean citedExpected = expectsAnswer && evalCase.source() != null
                && cited.contains(documents.get(evalCase.source()).toString());
        return new CaseResult(evalCase, outcome, answer, factsFound, citedExpected);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void writeReport(List<CaseResult> results, Metrics metrics) throws Exception {
        StringBuilder report = new StringBuilder()
                .append("# Answer quality\n\n")
                .append("Chat model: `").append(chatModel).append("`, embeddings: `").append(embeddingModel)
                .append("`\n\n")
                .append("| Metric | Value | Minimum |\n|---|---|---|\n")
                .append(row("Outcome accuracy", metrics.outcomeAccuracy(), minOutcomeAccuracy))
                .append(row("Fact recall", metrics.factRecall(), minFactRecall))
                .append(row("Citation accuracy", metrics.citationAccuracy(), minCitationAccuracy))
                .append("\n| Question | Expected | Got | Facts | Cited source |\n|---|---|---|---|---|\n");
        for (CaseResult result : results) {
            EvalCase evalCase = result.evalCase();
            report.append("| ").append(evalCase.question().replace("|", "\\|"))
                    .append(" | ").append(evalCase.expect())
                    .append(" | ").append(result.outcome())
                    .append(" | ").append(evalCase.facts() == null ? "-"
                            : result.factsFound() + "/" + evalCase.facts().size())
                    .append(" | ").append(evalCase.source() == null ? "-" : result.citedExpected() ? "yes" : "no")
                    .append(" |\n");
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8);
    }

    private static String row(String name, double value, double minimum) {
        return "| %s | %.2f | %.2f |%n".formatted(name, value, minimum);
    }

    /**
     * @param expect ANSWERED or NO_ANSWER
     * @param source the file the answer must cite, for ANSWERED cases
     * @param facts  phrases the answer must contain, compared without accents or case
     */
    record EvalCase(String question, String expect, String source, List<String> facts) {
    }

    record CaseResult(EvalCase evalCase, String outcome, String answer, int factsFound, boolean citedExpected) {
    }

    record Metrics(double outcomeAccuracy, double factRecall, double citationAccuracy) {

        static Metrics of(List<CaseResult> results) {
            long correctOutcomes = results.stream()
                    .filter(result -> result.outcome().equals(result.evalCase().expect()))
                    .count();
            List<CaseResult> answerable = results.stream()
                    .filter(result -> result.evalCase().expect().equals("ANSWERED"))
                    .toList();
            int expectedFacts = answerable.stream().mapToInt(result -> result.evalCase().facts().size()).sum();
            int foundFacts = answerable.stream().mapToInt(CaseResult::factsFound).sum();
            long cited = answerable.stream().filter(CaseResult::citedExpected).count();
            return new Metrics(
                    ratio(correctOutcomes, results.size()),
                    ratio(foundFacts, expectedFacts),
                    ratio(cited, answerable.size()));
        }

        private static double ratio(long part, long whole) {
            return whole == 0 ? 1 : (double) part / whole;
        }
    }
}

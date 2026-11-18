package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter.Routing;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelRouterTest {

    // Relevance starts at 0.6 here: a document counts within a quarter of the best passage's lead over it
    private final ModelRouter router = new ModelRouter(true, new ModelRouter.Thresholds(3, 0.25, 0.6, 0.6));

    @Test
    void aDirectQuestionGoesToTheCheapModel() {
        assertThat(router.route("¿Cuánto tarda el envío?", List.of(source(1))))
                .isEqualTo(new Routing(ModelTier.SIMPLE, ModelRouter.SIMPLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Qué diferencia hay entre el plan básico y el pro?",
            "¿Por qué el envío express cuesta más?",
            "¿Cuál es mejor para una tienda pequeña?",
            "¿Qué ventajas tiene pagar al contado?",
            "Which is better for a small shop?",
            "What are the pros and cons of the yearly plan?",
            "Qual é melhor, o plano anual ou o mensal?",
            "Quais as vantagens do plano anual?",
            "Pourquoi la livraison express coûte plus cher ?",
            "Lequel est le moins cher ?",
            "Vous me conseillez quelle formule ?",
            "Compara los dos planes",
    })
    void questionsThatAskToReasonGoToTheCapableModel(String question) {
        assertThat(router.route(question, List.of(source(1))).reason()).isEqualTo(ModelRouter.REASONING);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Si compro 3 y uno llega roto, ¿me devuelven todo o solo uno?",
            "¿Qué hago si el paquete llega abierto?",
            "If I order two and one is damaged, do I get a full refund?",
            "Se eu cancelar hoje, recebo o dinheiro de volta?",
            "O que acontece se eu cancelar?",
            "Et si je change d'adresse après la commande ?",
            "En caso de que no esté en casa, ¿dejan el paquete con un vecino?",
    })
    void aCaseTheCustomerImaginesGoesToTheCapableModel(String question) {
        assertThat(router.route(question, List.of(source(1))).reason()).isEqualTo(ModelRouter.HYPOTHETICAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Por qué mi plan cuesta más que el de mi amigo?",
            "¿Es más barato que comprar en tienda?",
            "Is the yearly plan cheaper than paying monthly?",
            "O plano anual é mais caro do que o mensal?",
            "C'est plus cher que la livraison normale ?",
    })
    void aComparisonGoesToTheCapableModelEvenAboutTheCustomersOwnCase(String question) {
        assertThat(router.route(question, List.of(source(1))).reason()).isEqualTo(ModelRouter.COMPARISON);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Si, ¿tienen talla 30?",
            "¿Sí tienen envío a Lima?",
            "¿Se puede pagar con tarjeta?",
            "¿Envían pedidos de más de 10 kg?",
            "Do you ship orders of more than 10 kg?",
    })
    void whatOnlyLooksLikeAConditionOrAComparisonIsALookup(String question) {
        assertThat(router.route(question, List.of(source(1))).tier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void theDocumentRuleMeansTheSameOnAnySimilarityScale() {
        // An embedding model whose similarities are all lower: the same spread counts the same documents
        ModelRouter lowScale = new ModelRouter(true, new ModelRouter.Thresholds(3, 0.25, 0.6, 0.2));
        List<Source> sources = List.of(
                source(1, 0.50, "El envío nacional tarda de 3 a 5 días hábiles."),
                source(2, 0.45, "Las devoluciones se aceptan durante 30 días con el ticket."),
                source(3, 0.43, "Pagamos con tarjeta, transferencia o efectivo en tienda."));

        assertThat(lowScale.route("Horario", sources).reason()).isEqualTo(ModelRouter.SEVERAL_DOCUMENTS);
        assertThat(router.route("Horario", sources).tier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void whatWasLearnedSendsTheQuestionToTheCapableModel() {
        assertThat(ModelRouter.learned(false)).isEqualTo(new Routing(ModelTier.COMPLEX, ModelRouter.LEARNED));
        assertThat(ModelRouter.learned(true)).isEqualTo(new Routing(ModelTier.COMPLEX, ModelRouter.UNSATISFIED));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Por qué no ha llegado mi pedido?",
            "¿Por qué me cobraron dos veces?",
            "¿Por qué no puedo entrar a mi cuenta?",
            "Why hasn't my order arrived?",
            "Why was I charged twice?",
            "Por que meu pedido não chegou?",
            "Pourquoi je n'ai pas reçu ma commande ?",
            "¿Cómo funciona el envío?",
            "Should I bring my ID?",
    })
    void aQuestionAboutTheCustomersOwnCaseIsALookup(String question) {
        assertThat(router.route(question, List.of(source(1))).tier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void cuesMatchWholeWordsOnly() {
        // " versus " is a cue, but not inside "diversus"
        assertThat(router.route("¿Tienen talla diversus?", List.of()).tier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void severalQuestionsOrALongQuestionGoToTheCapableModel() {
        assertThat(router.route("¿Envían a Lima? ¿Cuánto cuesta?", List.of()).reason())
                .isEqualTo(ModelRouter.SEVERAL_QUESTIONS);
        assertThat(router.route("a".repeat(ModelRouter.LONG_QUESTION_CHARS + 1), List.of()).reason())
                .isEqualTo(ModelRouter.LONG_QUESTION);
    }

    @Test
    void answersThatCombineSeveralDocumentsGoToTheCapableModel() {
        List<Source> sources = List.of(
                source(1, 0.82, "El envío nacional tarda de 3 a 5 días hábiles."),
                source(2, 0.80, "Las devoluciones se aceptan durante 30 días con el ticket."),
                source(3, 0.79, "Pagamos con tarjeta, transferencia o efectivo en tienda."));

        assertThat(router.route("Horario", sources).reason()).isEqualTo(ModelRouter.SEVERAL_DOCUMENTS);
    }

    @Test
    void documentsFarLessRelevantThanTheBestDoNotCount() {
        List<Source> sources = List.of(
                source(1, 0.82, "El envío nacional tarda de 3 a 5 días hábiles."),
                source(2, 0.70, "Las devoluciones se aceptan durante 30 días con el ticket."),
                source(3, 0.65, "Pagamos con tarjeta, transferencia o efectivo en tienda."));

        assertThat(router.route("Horario", sources).tier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void pagesThatRepeatTheSameTextCountOnce() {
        // The same footer or index on every page of a site is one piece of information, not three
        String index = "Índice alfabético de temas de historia de México: conquista, independencia, revolución.";
        List<Source> sources = List.of(
                source(1, 0.82, index + " Letra A"),
                source(2, 0.81, index + " Letra B"),
                source(3, 0.80, index + " Letra C"));

        assertThat(router.distinctDocuments(sources)).isEqualTo(1);
        assertThat(router.route("Historia", sources).tier()).isEqualTo(ModelTier.SIMPLE);
    }

    @Test
    void passagesFoundOnlyByTheirWordsDoNotCount() {
        List<Source> sources = List.of(
                source(1, 0.82, "El envío nacional tarda de 3 a 5 días hábiles."),
                source(2, null, "Las devoluciones se aceptan durante 30 días con el ticket."),
                source(3, null, "Pagamos con tarjeta, transferencia o efectivo en tienda."));

        assertThat(router.distinctDocuments(sources)).isEqualTo(1);
    }

    @Test
    void disabledRoutingSendsEverythingToTheCapableModel() {
        assertThat(new ModelRouter(false).route("Hola", List.of()))
                .isEqualTo(new Routing(ModelTier.COMPLEX, ModelRouter.DISABLED));
    }

    private static Source source(int number) {
        return source(number, 0.8, "content");
    }

    private static Source source(int number, Double similarity, String content) {
        return new Source(number, UUID.randomUUID(), UUID.randomUUID(), "Doc " + number, content, similarity,
                similarity == null);
    }
}

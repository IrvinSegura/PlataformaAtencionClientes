package com.irvinsegura.supportchat.knowledge.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import org.junit.jupiter.api.Test;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    @Test
    void vectorsHaveUnitLength() {
        assertThat(norm(model.embedQuery("How long does shipping take?"))).isCloseTo(1.0, within(1e-5));
        assertThat(norm(model.embedQuery("?!"))).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void sameTextGivesTheSameVector() {
        assertThat(model.embedQuery("Refund policy")).isEqualTo(model.embedPassages(java.util.List.of("Refund policy")).getFirst());
    }

    @Test
    void relatedTextIsCloserThanUnrelatedText() {
        Embedding question = model.embedQuery("¿Cuánto tarda el envío?");
        Embedding related = model.embedQuery("El envío tarda de 3 a 5 días hábiles.");
        Embedding unrelated = model.embedQuery("Aceptamos tarjetas de crédito y débito.");

        assertThat(cosine(question, related)).isGreaterThan(cosine(question, unrelated));
    }

    @Test
    void sharedStopWordsDoNotMakeUnrelatedTextSimilar() {
        Embedding question = model.embedQuery("¿Quién ganó el mundial de futbol de 1986?");
        Embedding passage = model.embedQuery("El envío nacional tarda de 3 a 5 días hábiles y es gratis en compras "
                + "mayores a 999 pesos. Cuando el pedido sale del almacén recibes un correo de la paquetería.");

        assertThat(cosine(question, passage)).isLessThan(0.1);
    }

    @Test
    void accentsAndCaseDoNotChangeTheVector() {
        assertThat(model.embedQuery("ENVÍO rápido")).isEqualTo(model.embedQuery("envio rapido"));
    }

    private static double norm(Embedding embedding) {
        return Math.sqrt(cosine(embedding, embedding));
    }

    private static double cosine(Embedding left, Embedding right) {
        float[] a = left.values();
        float[] b = right.values();
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}

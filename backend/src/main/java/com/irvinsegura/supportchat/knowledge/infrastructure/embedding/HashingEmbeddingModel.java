package com.irvinsegura.supportchat.knowledge.infrastructure.embedding;

import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import com.irvinsegura.supportchat.shared.domain.SearchTerms;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Offline embeddings by feature hashing: words and character trigrams are hashed into a fixed number of dimensions.
 * Two texts score high when they share vocabulary, including inflected forms ("envío", "envíos") through the
 * trigrams. Stop words are left out and repeated words count less each time, so a short question is not pulled
 * towards long passages by the filler words they share. It understands no synonyms, so it is meant for development, tests and demos without an API key; real
 * workspaces use a neural model such as Voyage.
 */
class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = KnowledgeLimits.EMBEDDING_DIMENSIONS;
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final float WORD_WEIGHT = 1.0f;
    private static final float TRIGRAM_WEIGHT = 0.5f;

    @Override
    public List<Embedding> embedPassages(List<String> passages) {
        return passages.stream().map(HashingEmbeddingModel::embed).toList();
    }

    @Override
    public Embedding embedQuery(String query) {
        return embed(query);
    }

    // Short questions share only a few words with a whole passage, so related pairs score lower than with a neural
    // model: a two-word question matching one word of a paragraph lands around 0.14
    @Override
    public double relatedSimilarity() {
        return 0.2;
    }

    // Rephrasings that keep the main words ("¿Hacen envíos a Colombia?", "¿Envían a Colombia?") score 0.4 to 0.6;
    // unrelated questions stay under 0.2. Synonyms are missed: "devolver" and "reembolso" never meet
    @Override
    public double sameTopicSimilarity() {
        return 0.3;
    }

    // Hashing only sees shared words, so "¿Cuánto tarda el envío nacional?" and "¿El envío nacional es gratis?" score
    // 0.7 while meaning different things; only wordings that are almost the same count as one question
    @Override
    public double sameQuestionSimilarity() {
        return 0.9;
    }

    static Embedding embed(String text) {
        float[] vector = new float[DIMENSIONS];
        String folded = MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String word : NON_WORD.split(folded)) {
            // Words every text shares ("de", "el", "que") would make unrelated texts look alike
            if (SearchTerms.isMeaningful(word)) {
                counts.merge(word, 1, Integer::sum);
            }
        }
        counts.forEach((word, count) -> {
            // Sublinear: a word said five times is a stronger signal than once, not five times stronger
            float repeats = (float) (1 + Math.log(count));
            add(vector, "w:" + word, WORD_WEIGHT * repeats);
            String padded = "^" + word + "$";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                add(vector, "t:" + padded.substring(i, i + 3), TRIGRAM_WEIGHT * repeats);
            }
        });
        normalize(vector);
        return new Embedding(vector);
    }

    // The sign comes from another bit of the hash, so collisions cancel out on average instead of piling up
    private static void add(float[] vector, String feature, float weight) {
        int hash = fnv1a(feature);
        int index = Math.floorMod(hash, DIMENSIONS);
        vector[index] += (hash & 0x8000_0000) == 0 ? weight : -weight;
    }

    private static void normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            // Text without a single word: any fixed unit vector keeps cosine distance defined
            vector[0] = 1;
            return;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }

    private static int fnv1a(String value) {
        int hash = 0x811C9DC5;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }
}

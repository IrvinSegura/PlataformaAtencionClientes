package com.irvinsegura.supportchat.knowledge.application;

import com.irvinsegura.supportchat.knowledge.TaughtAnswers;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A taught answer is a Markdown document whose heading is the question, so the question's own words lead the search
 * to it, and whose body is the team's answer.
 */
@Service
class TaughtAnswersService implements TaughtAnswers {

    static final String TITLE_PREFIX = "Respuesta del equipo: ";

    private final DocumentService documents;

    TaughtAnswersService(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    @Transactional
    public UUID teach(String question, String answer, UUID taughtBy) {
        String asked = oneLine(question);
        String text = "# " + asked + "\n\n" + answer.strip() + "\n";
        String title = shorten(TITLE_PREFIX + asked, KnowledgeLimits.MAX_TITLE_LENGTH);
        return documents.upload(title, "respuesta-del-equipo.md", text.getBytes(StandardCharsets.UTF_8), taughtBy)
                .id();
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}

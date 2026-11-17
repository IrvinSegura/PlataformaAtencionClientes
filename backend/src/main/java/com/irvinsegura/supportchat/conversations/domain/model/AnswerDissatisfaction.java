package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.QuestionKey;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tells from the customer's next message that the assistant's last answer did not help, so the question that got it
 * can be learned as difficult: either they say so ("no entendí", "that's not what I asked") or they ask the same
 * question again in other words.
 */
public final class AnswerDissatisfaction {

    // Similarity of words from which a new question asks again what the earlier one asked; high, because the next
    // question of a conversation often shares most words with the last one and asks something else
    static final double ASKED_AGAIN_SIMILARITY = 0.7;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    // Matched against the folded message with spaces around it
    private static final List<String> PHRASES = List.of(
            // Spanish
            " no entendi ", " no te entendi ", " no entiendo tu respuesta ", " sigo sin entender ", " no me sirve ",
            " no me sirvio ", " no me ayuda ", " no me ayudo ", " no me ayudaste ", " no es lo que pregunte ",
            " eso no es lo que pregunte ", " no respondiste ", " no contestaste ", " no me respondiste ",
            " eso no responde ", " no responde mi pregunta ", " no es eso ", " no tiene sentido ",
            // Not a bare "está mal": "mi dirección está mal" asks for a change
            " eso esta mal ", " esta respuesta esta mal ", " te equivocas ",
            // English
            " that doesn t help ", " that didn t help ", " doesn t help ", " not helpful ", " not what i asked ",
            " you didn t answer ", " doesn t answer ", " didn t answer ", " i don t understand your answer ",
            " that s wrong ", " that is wrong ", " that s not right ", " makes no sense ",
            // Portuguese
            " nao entendi ", " nao ajudou ", " nao me ajudou ", " nao foi isso que perguntei ", " nao respondeu ",
            " isso esta errado ", " nao faz sentido ",
            // French
            " je n ai pas compris ", " ca ne m aide pas ", " ce n est pas ma question ", " vous n avez pas repondu ",
            " c est faux ", " ca n a pas de sens ");

    private AnswerDissatisfaction() {
    }

    /**
     * Whether the customer says the last answer did not help.
     */
    public static boolean saysSo(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String folded = " " + NON_WORD.matcher(MARKS.matcher(Normalizer.normalize(
                message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("")).replaceAll(" ").strip() + " ";
        return PHRASES.stream().anyMatch(folded::contains);
    }

    /**
     * Whether {@code question} asks again what {@code earlier} asked, in the same or other words.
     */
    public static boolean asksAgain(String earlier, String question) {
        if (earlier == null || question == null) {
            return false;
        }
        if (QuestionKey.of(earlier).equals(QuestionKey.of(question)) && !QuestionKey.of(question).isEmpty()) {
            return true;
        }
        return QuestionWords.similarity(QuestionWords.of(earlier), QuestionWords.of(question))
                >= ASKED_AGAIN_SIMILARITY;
    }
}
